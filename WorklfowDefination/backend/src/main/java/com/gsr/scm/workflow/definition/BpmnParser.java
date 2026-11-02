package com.gsr.scm.workflow.definition;

import com.gsr.scm.workflow.definition.BpmnModel.Flow;
import com.gsr.scm.workflow.definition.BpmnModel.Kind;
import com.gsr.scm.workflow.definition.BpmnModel.Node;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Parses BPMN 2.0 XML (with Zeebe extensions) into a {@link BpmnModel}. XXE-safe. */
public final class BpmnParser {

    public static final String BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    public static final String ZEEBE_NS = "http://camunda.org/schema/zeebe/1.0";

    private static final Set<String> IGNORED = Set.of("sequenceFlow", "extensionElements", "documentation",
            "laneSet", "textAnnotation", "association", "dataObject", "dataObjectReference", "dataStoreReference",
            "incoming", "outgoing");

    private BpmnParser() {
    }

    public static class BpmnParseException extends RuntimeException {
        public BpmnParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static BpmnModel parse(String xml) {
        Document doc = read(xml);
        NodeList processes = doc.getElementsByTagNameNS(BPMN_NS, "process");
        if (processes.getLength() == 0) {
            throw new BpmnParseException("The BPMN document contains no <bpmn:process>", null);
        }
        if (processes.getLength() > 1) {
            throw new BpmnParseException("Only one process per BPMN document is supported (collaborations with "
                    + "several executable pools are not)", null);
        }
        Element process = (Element) processes.item(0);
        Map<String, Flow> flows = new LinkedHashMap<>();
        List<Element> elements = childElements(process);
        for (Element el : elements) {
            if (BPMN_NS.equals(el.getNamespaceURI()) && "sequenceFlow".equals(el.getLocalName())) {
                Element cond = firstChild(el, BPMN_NS, "conditionExpression");
                flows.put(el.getAttribute("id"), new Flow(el.getAttribute("id"), attr(el, "name"),
                        el.getAttribute("sourceRef"), el.getAttribute("targetRef"),
                        cond == null ? null : cond.getTextContent().trim()));
            }
        }
        Map<String, Node> nodes = new LinkedHashMap<>();
        for (Element el : elements) {
            if (!BPMN_NS.equals(el.getNamespaceURI()) || IGNORED.contains(el.getLocalName())) {
                continue;
            }
            String id = el.getAttribute("id");
            String tag = el.getLocalName();
            Kind kind = switch (tag) {
                case "startEvent" -> Kind.START_EVENT;
                case "endEvent" -> Kind.END_EVENT;
                case "serviceTask" -> Kind.SERVICE_TASK;
                case "userTask" -> Kind.USER_TASK;
                case "exclusiveGateway" -> Kind.EXCLUSIVE_GATEWAY;
                default -> Kind.UNSUPPORTED;
            };
            Element ext = firstChild(el, BPMN_NS, "extensionElements");
            String jobType = null;
            Integer retries = null;
            List<String> groups = List.of();
            String dueDate = null;
            boolean camundaUserTask = false;
            if (ext != null) {
                Element taskDef = firstChild(ext, ZEEBE_NS, "taskDefinition");
                if (taskDef != null) {
                    jobType = attr(taskDef, "type");
                    String r = attr(taskDef, "retries");
                    retries = r == null ? null : parseIntOrNull(r);
                }
                Element assignment = firstChild(ext, ZEEBE_NS, "assignmentDefinition");
                if (assignment != null && attr(assignment, "candidateGroups") != null) {
                    groups = Arrays.stream(attr(assignment, "candidateGroups").split(","))
                            .map(String::trim).filter(s -> !s.isEmpty()).toList();
                }
                Element schedule = firstChild(ext, ZEEBE_NS, "taskSchedule");
                if (schedule != null) {
                    dueDate = attr(schedule, "dueDate");
                }
                camundaUserTask = firstChild(ext, ZEEBE_NS, "userTask") != null;
            }
            boolean hasEventDefinition = childElements(el).stream()
                    .anyMatch(c -> c.getLocalName() != null && c.getLocalName().endsWith("EventDefinition"));
            List<String> incoming = new ArrayList<>();
            List<String> outgoing = new ArrayList<>();
            flows.values().forEach(f -> {
                if (id.equals(f.targetRef())) {
                    incoming.add(f.id());
                }
                if (id.equals(f.sourceRef())) {
                    outgoing.add(f.id());
                }
            });
            nodes.put(id, new Node(id, attr(el, "name"), kind, tag, jobType, retries, groups, dueDate,
                    attr(el, "default"), camundaUserTask, hasEventDefinition, List.copyOf(incoming),
                    List.copyOf(outgoing)));
        }
        return new BpmnModel(process.getAttribute("id"), attr(process, "name"),
                "true".equalsIgnoreCase(process.getAttribute("isExecutable")), nodes, flows);
    }

    private static Document read(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new BpmnParseException("BPMN XML is empty", null);
        }
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            DocumentBuilder builder = f.newDocumentBuilder();
            builder.setErrorHandler(null);
            return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (SAXException e) {
            throw new BpmnParseException("BPMN XML is not well-formed: " + e.getMessage(), e);
        } catch (ParserConfigurationException | IOException e) {
            throw new BpmnParseException("Could not read BPMN XML: " + e.getMessage(), e);
        }
    }

    private static List<Element> childElements(Element parent) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element e) {
                result.add(e);
            }
        }
        return result;
    }

    private static Element firstChild(Element parent, String ns, String localName) {
        for (Element e : childElements(parent)) {
            if (ns.equals(e.getNamespaceURI()) && localName.equals(e.getLocalName())) {
                return e;
            }
        }
        return null;
    }

    private static String attr(Element el, String name) {
        String v = el.getAttribute(name);
        return v == null || v.isEmpty() ? null : v;
    }

    private static Integer parseIntOrNull(String s) {
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
