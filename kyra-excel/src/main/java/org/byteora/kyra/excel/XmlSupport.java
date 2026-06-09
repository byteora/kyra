package org.byteora.kyra.excel;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

final class XmlSupport {
    private XmlSupport() {
    }

    static Document parse(byte[] bytes) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid xlsx XML part", ex);
        }
    }

    static List<Element> children(Element element, String name) {
        List<Element> children = new ArrayList<>();
        NodeList nodes = element.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element child && tagName(child).equals(name)) {
                children.add(child);
            }
        }
        return children;
    }

    static Element child(Element element, String name) {
        return children(element, name).stream().findFirst().orElse(null);
    }

    static List<Element> descendants(Element element, String name) {
        List<Element> elements = new ArrayList<>();
        collect(element, name, elements);
        return elements;
    }

    static String text(Element element) {
        return element == null ? null : element.getTextContent();
    }

    static String attr(Element element, String name) {
        if (element == null) {
            return "";
        }
        if (element.hasAttribute(name)) {
            return element.getAttribute(name);
        }
        int colon = name.indexOf(':');
        if (colon > 0 && element.hasAttribute(name.substring(colon + 1))) {
            return element.getAttribute(name.substring(colon + 1));
        }
        return "";
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static void collect(Element element, String name, List<Element> elements) {
        NodeList nodes = element.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element child) {
                if (tagName(child).equals(name)) {
                    elements.add(child);
                }
                collect(child, name, elements);
            }
        }
    }

    private static String tagName(Element element) {
        String tagName = element.getTagName();
        int colon = tagName.indexOf(':');
        return colon < 0 ? tagName : tagName.substring(colon + 1);
    }
}
