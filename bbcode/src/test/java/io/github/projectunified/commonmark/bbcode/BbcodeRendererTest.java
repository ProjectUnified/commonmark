package io.github.projectunified.commonmark.bbcode;

import org.commonmark.node.CustomNode;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BbcodeRendererTest {

    private final Parser parser = Parser.builder().build();
    private final BbcodeRenderer renderer = BbcodeRenderer.builder().build();

    private Node parse(String markdown) {
        return parser.parse(markdown);
    }

    @Test
    public void testBasicInlineStyles() {
        String markdown = "This is **bold** and *italic* text.";
        String expected = "This is [b]bold[/b] and [i]italic[/i] text.\n";
        assertEquals(expected, renderer.render(parse(markdown)));
    }

    @Test
    public void testHeadings() {
        String markdown = "# Heading 1\n## Heading 2\n### Heading 3";
        String expected = "[size=6][b]Heading 1[/b][/size]\n\n[size=5][b]Heading 2[/b][/size]\n\n[size=4][b]Heading 3[/b][/size]\n";
        assertEquals(expected, renderer.render(parse(markdown)));
    }

    @Test
    public void testCustomHeadingSizes() {
        BbcodeRenderer customRenderer = BbcodeRenderer.builder()
                .headingSize(1, "100")
                .headingSize(2, null) // remove size tag
                .build();

        String markdown = "# Heading 1\n## Heading 2";
        String expected = "[size=100][b]Heading 1[/b][/size]\n\n[b]Heading 2[/b]\n";
        assertEquals(expected, customRenderer.render(parse(markdown)));
    }

    @Test
    public void testBlockQuote() {
        String markdown = "> This is a quote.\n> Second line.";
        String expected = "[quote]\nThis is a quote.\nSecond line.\n[/quote]\n";
        assertEquals(expected, renderer.render(parse(markdown)));
    }

    @Test
    public void testUnorderedList() {
        String markdown = "- Item 1\n- Item 2";
        String expected = "[list]\n[*]Item 1\n[*]Item 2\n[/list]\n";
        assertEquals(expected, renderer.render(parse(markdown)));
    }

    @Test
    public void testOrderedList() {
        String markdown = "1. First\n2. Second";
        String expected = "[list=1]\n[*]First\n[*]Second\n[/list]\n";
        assertEquals(expected, renderer.render(parse(markdown)));
    }

    @Test
    public void testOrderedListCustomStart() {
        String markdown = "3. Three\n4. Four";
        String expected = "[list=3]\n[*]Three\n[*]Four\n[/list]\n";
        assertEquals(expected, renderer.render(parse(markdown)));
    }

    @Test
    public void testCode() {
        String markdown = "Use `code` tag.";
        String expected = "Use [code]code[/code] tag.\n";
        assertEquals(expected, renderer.render(parse(markdown)));
    }

    @Test
    public void testCodeBlock() {
        String markdown = "```java\nint a = 10;\n```";
        String expected = "[code]int a = 10;\n[/code]\n";
        assertEquals(expected, renderer.render(parse(markdown)));
    }

    @Test
    public void testLinkAndImage() {
        String markdown = "[Google](https://google.com) and ![Logo](logo.png)";
        String expected = "[url=https://google.com]Google[/url] and [img]logo.png[/img]\n";
        assertEquals(expected, renderer.render(parse(markdown)));
    }

    @Test
    public void testThematicBreak() {
        String markdown = "---\n";
        String expected = "[hr]\n";
        assertEquals(expected, renderer.render(parse(markdown)));
    }

    @Test
    public void testCustomThematicBreak() {
        BbcodeRenderer customRenderer = BbcodeRenderer.builder()
                .thematicBreakTag("[thematic-break]")
                .build();
        String markdown = "---\n";
        String expected = "[thematic-break]\n";
        assertEquals(expected, customRenderer.render(parse(markdown)));
    }

    @Test
    public void testCustomNodeStrikethrough() {
        org.commonmark.ext.gfm.strikethrough.Strikethrough strike = new org.commonmark.ext.gfm.strikethrough.Strikethrough();
        strike.appendChild(new org.commonmark.node.Text("deleted text"));
        
        Node document = new org.commonmark.node.Document();
        document.appendChild(strike);
        
        String expected = "[s]deleted text[/s]";
        assertEquals(expected, renderer.render(document));
    }
}
