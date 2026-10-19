package com.samplemvcwebapp.testsupport;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Reads a seed XML resource into a simple blog/post/tag-slug shape (used to check the seed files themselves). */
public final class SeedXml {

    public record SeedPost(String title, List<String> tagSlugs) { }

    public record SeedBlog(String name, String email, List<SeedPost> posts) { }

    private SeedXml() { }

    public static List<SeedBlog> formBlogsWithPosts(String classpathResource) {
        InputStream in = SeedXml.class.getClassLoader().getResourceAsStream(classpathResource);
        if (in == null) {
            throw new IllegalArgumentException("Could not find the xml file you asked for. Name = " + classpathResource);
        }
        try (in) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = factory.newDocumentBuilder().parse(in);
            List<SeedBlog> blogs = new ArrayList<>();
            NodeList blogNodes = doc.getElementsByTagName("Blog");
            for (int i = 0; i < blogNodes.getLength(); i++) {
                Element blog = (Element) blogNodes.item(i);
                List<SeedPost> posts = new ArrayList<>();
                NodeList postNodes = blog.getElementsByTagName("Post");
                for (int j = 0; j < postNodes.getLength(); j++) {
                    Element post = (Element) postNodes.item(j);
                    Set<String> slugs = new LinkedHashSet<>();
                    for (String s : text(post, "TagSlugs").split(",")) {
                        if (!s.isBlank()) slugs.add(s.trim());
                    }
                    posts.add(new SeedPost(text(post, "Title"), List.copyOf(slugs)));
                }
                blogs.add(new SeedBlog(text(blog, "Name"), text(blog, "Email"), posts));
            }
            return blogs;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read " + classpathResource, e);
        }
    }

    private static String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
    }
}
