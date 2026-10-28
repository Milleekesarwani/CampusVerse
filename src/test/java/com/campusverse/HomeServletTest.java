package com.campusverse;

import com.campusverse.servlet.HomeServlet;
import jakarta.servlet.annotation.WebServlet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class HomeServletTest {

    @Test
    void testHomeServletAnnotation() {
        WebServlet annotation = HomeServlet.class.getAnnotation(WebServlet.class);
        assertNotNull(annotation, "HomeServlet must have @WebServlet annotation");
        assertArrayEquals(new String[]{"/home", ""}, annotation.urlPatterns());
        assertEquals("HomeServlet", annotation.name());
    }

    @Test
    void testHomeServletInstantiation() {
        HomeServlet servlet = new HomeServlet();
        assertNotNull(servlet);
    }
}

