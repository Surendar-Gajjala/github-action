// VERIFIED: plain JAXP (JDK). Hardened against XXE / entity expansion as recommended by OWASP.
package com.example.ectr.plugin.core.config;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Small helpers for reading our rule XML files safely. */
public final class SecureXml {

    private SecureXml() {
    }

    /**
     * Opens a rule file: an external file path when {@code externalPath} is non-blank,
     * otherwise the default resource packaged in this bundle.
     */
    public static Element loadRoot(String externalPath, String bundledResource, String expectedRoot) {
        if (externalPath != null && !externalPath.isBlank()) {
            Path path = Path.of(externalPath.trim());
            if (!Files.isRegularFile(path)) {
                throw new RuleConfigurationException("Rule file not found: " + path.toAbsolutePath());
            }
            try (InputStream in = Files.newInputStream(path)) {
                return parse(in, path.toString(), expectedRoot);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read rule file " + path, e);
            }
        }
        URL url = SecureXml.class.getClassLoader().getResource(bundledResource);
        if (url == null) {
            throw new RuleConfigurationException("Bundled rule file missing from the plug-in jar: " + bundledResource);
        }
        try (InputStream in = url.openStream()) {
            return parse(in, bundledResource, expectedRoot);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read bundled rule file " + bundledResource, e);
        }
    }

    public static Element parse(InputStream in, String sourceName, String expectedRoot) {
        try {
            DocumentBuilder builder = newFactory().newDocumentBuilder();
            builder.setErrorHandler(null); // report errors via exceptions only, not stderr
            Document document = builder.parse(in);
            Element root = document.getDocumentElement();
            if (!expectedRoot.equals(root.getTagName())) {
                throw new RuleConfigurationException(sourceName + ": expected root element <" + expectedRoot
                        + "> but found <" + root.getTagName() + ">");
            }
            return root;
        } catch (SAXException e) {
            throw new RuleConfigurationException(sourceName + ": invalid XML: " + e.getMessage(), e);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("XML parser cannot be configured securely", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<Element> childElements(Element parent) {
        List<Element> result = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e) {
                result.add(e);
            }
        }
        return result;
    }

    public static String requiredAttribute(Element element, String name, String context) {
        String value = element.getAttribute(name).trim();
        if (value.isEmpty()) {
            throw new RuleConfigurationException(context + ": <" + element.getTagName()
                    + "> needs attribute '" + name + "'");
        }
        return value;
    }

    private static DocumentBuilderFactory newFactory() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(false);
        return factory;
    }
}
