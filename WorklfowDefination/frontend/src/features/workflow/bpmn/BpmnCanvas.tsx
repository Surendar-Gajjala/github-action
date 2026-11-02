/* eslint-disable @typescript-eslint/no-explicit-any */
import { useEffect, useRef, useState } from 'react';
import Modeler from 'bpmn-js/lib/Modeler';
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer';
import zeebeModdle from 'zeebe-bpmn-moddle/resources/zeebe.json';
import 'bpmn-js/dist/assets/diagram-js.css';
import 'bpmn-js/dist/assets/bpmn-js.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css';

export type BpmnInstance = Modeler | NavigatedViewer;

interface Props {
  xml: string;
  readOnly?: boolean;
  /** Element ids to highlight (e.g. the current stage of a running instance). */
  highlight?: string[];
  onReady?: (instance: BpmnInstance) => void;
  onSelect?: (element: any | null) => void;
  onChange?: () => void;
  height?: number | string;
}

/**
 * bpmn-js canvas. Editing uses the Modeler with the Zeebe moddle extension so zeebe:* extension elements
 * (task definitions, candidate groups) are preserved and editable; read-only views use the NavigatedViewer.
 */
export function BpmnCanvas({ xml, readOnly, highlight, onReady, onSelect, onChange, height = 560 }: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const instanceRef = useRef<BpmnInstance | null>(null);
  const [error, setError] = useState<string | null>(null);
  const callbacks = useRef({ onReady, onSelect, onChange });
  callbacks.current = { onReady, onSelect, onChange };

  useEffect(() => {
    const options = { container: containerRef.current!, moddleExtensions: { zeebe: zeebeModdle } };
    const instance: BpmnInstance = readOnly ? new NavigatedViewer(options) : new Modeler(options);
    instanceRef.current = instance;
    const bus: any = instance.get('eventBus');
    bus.on('selection.changed', (e: any) => callbacks.current.onSelect?.(e.newSelection[0] ?? null));
    // Importing a diagram clears the command stack, which is not a user change.
    bus.on('commandStack.changed', (e: any) => e?.trigger !== 'clear' && callbacks.current.onChange?.());
    return () => {
      instance.destroy();
      instanceRef.current = null;
    };
  }, [readOnly]);

  useEffect(() => {
    const instance = instanceRef.current;
    if (!instance || !xml) return;
    let cancelled = false;
    instance
      .importXML(xml)
      .then(() => {
        if (cancelled) return;
        setError(null);
        (instance.get('canvas') as any).zoom('fit-viewport', 'auto');
        callbacks.current.onReady?.(instance);
      })
      .catch((e: Error) => !cancelled && setError(e.message));
    return () => {
      cancelled = true;
    };
  }, [xml, readOnly]);

  useEffect(() => {
    const instance = instanceRef.current;
    if (!instance || !highlight?.length) return;
    const canvas: any = instance.get('canvas');
    const registry: any = instance.get('elementRegistry');
    const applied: string[] = [];
    const apply = () =>
      highlight.forEach((id) => {
        if (registry.get(id)) {
          canvas.addMarker(id, 'bpmn-active-element');
          applied.push(id);
        }
      });
    const timer = setTimeout(apply, 50);
    return () => {
      clearTimeout(timer);
      applied.forEach((id) => registry.get(id) && canvas.removeMarker(id, 'bpmn-active-element'));
    };
  }, [highlight, xml]);

  return (
    <div className="relative" style={{ height }}>
      <div ref={containerRef} className="bpmn-canvas h-full w-full" data-testid="bpmn-canvas" />
      {error && (
        <div className="absolute inset-x-4 top-4 rounded-md border border-rose-300 bg-rose-50 p-3 text-rose-800">
          Could not render the diagram: {error}
        </div>
      )}
    </div>
  );
}
