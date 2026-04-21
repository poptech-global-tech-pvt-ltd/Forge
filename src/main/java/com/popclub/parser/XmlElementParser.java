package com.popclub.parser;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XmlElementParser {

    public static List<Map<String, String>> parse(String xml) throws Exception {

        List<Map<String, String>> results = new ArrayList<>();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))
        );

        NodeList nodes = doc.getElementsByTagName("*");

        for (int i = 0; i < nodes.getLength(); i++) {

            Element element = (Element) nodes.item(i);
            Map<String, String> attrs = new HashMap<>();

            attrs.put("accessibilityId", element.getAttribute("content-desc"));
            attrs.put("resourceId",      element.getAttribute("resource-id"));
            attrs.put("text",            element.getAttribute("text"));
            attrs.put("class",           element.getAttribute("class"));

            results.add(attrs);
        }

        return results;
    }
}