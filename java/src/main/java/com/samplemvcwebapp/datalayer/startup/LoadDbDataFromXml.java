package com.samplemvcwebapp.datalayer.startup;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.springframework.core.io.ClassPathResource;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** Port of DataLayer/Startup/Internal/LoadDbDataFromXml.cs: parses a seed file into plain records (no persistence). */
final class LoadDbDataFromXml {

    record TagData(String name, String slug) { }

    record PostData(String title, String content, List<String> tagSlugs) { }

    record BlogData(String name, String email, List<PostData> posts) { }

    record SeedData(Map<String, TagData> tagsBySlug, List<BlogData> blogs) { }

    private LoadDbDataFromXml() {
    }

    static SeedData load(String classpathResource) {
        try (InputStream in = new ClassPathResource(classpathResource).getInputStream()) {
            Element root = newBuilder().parse(in).getDocumentElement();
            Map<String, TagData> tags = decodeTags(child(root, "Tags"));
            return new SeedData(tags, decodeBlogs(child(root, "Blogs")));
        } catch (IOException | SAXException | ParserConfigurationException e) {
            throw new IllegalStateException("Could not load seed data from " + classpathResource, e);
        }
    }

    private static DocumentBuilder newBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder();
    }

    private static Map<String, TagData> decodeTags(Element tagsElement) {
        Map<String, TagData> result = new LinkedHashMap<>();
        for (Element tagXml : children(tagsElement, "Tag")) {
            TagData tag = new TagData(text(tagXml, "Name"), text(tagXml, "Slug"));
            result.put(tag.slug(), tag);
        }
        return result;
    }

    private static List<BlogData> decodeBlogs(Element blogsElement) {
        List<BlogData> result = new ArrayList<>();
        for (Element blogXml : children(blogsElement, "Blog")) {
            List<PostData> posts = new ArrayList<>();
            for (Element postXml : children(child(blogXml, "Posts"), "Post")) {
                String trimmedContent = Arrays.stream(text(postXml, "Content").split("\n", -1))
                        .map(String::strip)
                        .collect(Collectors.joining("\n"));
                List<String> slugs = Arrays.stream(text(postXml, "TagSlugs").split(","))
                        .map(String::strip)
                        .toList();
                posts.add(new PostData(text(postXml, "Title"), trimmedContent, slugs));
            }
            result.add(new BlogData(text(blogXml, "Name"), text(blogXml, "Email"), posts));
        }
        return result;
    }

    private static Element child(Element parent, String name) {
        List<Element> found = children(parent, name);
        if (found.isEmpty())
            throw new IllegalStateException("Seed XML: <" + parent.getTagName() + "> has no <" + name + ">");
        return found.get(0);
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && name.equals(node.getNodeName()))
                result.add((Element) node);
        }
        return result;
    }

    private static String text(Element parent, String name) {
        return child(parent, name).getTextContent();
    }
}
