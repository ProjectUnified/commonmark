package io.github.projectunified.commonmark.poi;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PoiRendererTest {

    private final Parser parser = Parser.builder().build();
    private final PoiRenderer renderer = PoiRenderer.builder().build();

    private Node parse(String markdown) {
        return parser.parse(markdown);
    }

    @Test
    public void testRenderToDocument() throws IOException {
        String markdown = "# Heading 1\n" +
                "## Heading 2\n" +
                "This is a **bold** and *italic* paragraph with `code`.\n" +
                "\n" +
                "> This is a blockquote\n" +
                "\n" +
                "- Item 1\n" +
                "- Item 2\n" +
                "\n" +
                "1. First\n" +
                "2. Second\n" +
                "\n" +
                "```java\n" +
                "int a = 10;\n" +
                "```\n" +
                "\n" +
                "![Test Image](invalid-path.png)\n";

        XWPFDocument doc = new XWPFDocument();
        renderer.render(parse(markdown), doc);

        List<XWPFParagraph> paragraphs = doc.getParagraphs();
        assertFalse(paragraphs.isEmpty());

        // Validate Heading 1
        XWPFParagraph h1 = paragraphs.get(0);
        assertEquals("Heading 1", h1.getText());
        assertTrue(h1.getRuns().get(0).isBold());

        // Validate Heading 2
        XWPFParagraph h2 = paragraphs.get(1);
        assertEquals("Heading 2", h2.getText());
        assertTrue(h2.getRuns().get(0).isBold());

        // Validate Paragraph containing bold/italic/code
        XWPFParagraph p = paragraphs.get(2);
        assertTrue(p.getText().contains("bold"));
        assertTrue(p.getText().contains("italic"));
        assertTrue(p.getText().contains("code"));

        // Validate Blockquote
        XWPFParagraph bq = paragraphs.get(3);
        assertEquals("This is a blockquote", bq.getText());
        assertEquals(720, bq.getIndentationLeft());

        // Validate Bullet List
        XWPFParagraph bullet1 = paragraphs.get(4);
        assertTrue(bullet1.getText().startsWith("• "));
        assertTrue(bullet1.getText().contains("Item 1"));

        XWPFParagraph bullet2 = paragraphs.get(5);
        assertTrue(bullet2.getText().startsWith("• "));
        assertTrue(bullet2.getText().contains("Item 2"));

        // Validate Ordered List
        XWPFParagraph order1 = paragraphs.get(6);
        assertTrue(order1.getText().startsWith("1. "));
        assertTrue(order1.getText().contains("First"));

        XWPFParagraph order2 = paragraphs.get(7);
        assertTrue(order2.getText().startsWith("2. "));
        assertTrue(order2.getText().contains("Second"));

        // Validate Code Block
        XWPFParagraph code = paragraphs.get(8);
        assertEquals("int a = 10;", code.getText().trim());
        assertEquals("Courier New", code.getRuns().get(0).getFontFamily());

        // Validate Image Fallback
        XWPFParagraph imagePara = paragraphs.get(9);
        assertEquals("[Image: invalid-path.png]", imagePara.getText().trim());
    }

    @Test
    public void testRenderToParagraph() {
        String markdown = "Hello **bold** world";
        XWPFDocument doc = new XWPFDocument();
        XWPFParagraph paragraph = doc.createParagraph();

        renderer.render(parse(markdown), paragraph);

        assertEquals("Hello bold world", paragraph.getText());
        List<XWPFRun> runs = paragraph.getRuns();
        assertTrue(runs.size() >= 3);
        assertFalse(runs.get(0).isBold());
        assertTrue(runs.get(1).isBold());
        assertFalse(runs.get(2).isBold());
    }

    @Test
    public void testRenderToRichTextString() throws IOException {
        String markdown = "Plain **Bold** *Italic* `Code`";

        try (Workbook wb = new XSSFWorkbook()) {
            PoiRenderer richTextRenderer = PoiRenderer.builder()
                    .workbook(wb)
                    .build();

            XSSFRichTextString richText = new XSSFRichTextString();
            richTextRenderer.render(parse(markdown), richText);

            assertEquals("Plain Bold Italic Code\n", richText.getString());
            // It splits into runs internally. Let's make sure it is populated.
            assertTrue(richText.numFormattingRuns() > 0);
        }
    }

    @Test
    public void testRenderToExcelCell() throws IOException {
        String markdown = "Cell with **Bold** and *Italic*";

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Test");
            Row row = sheet.createRow(0);
            Cell cell = row.createCell(0);

            PoiRenderer cellRenderer = PoiRenderer.builder().build();
            cellRenderer.render(parse(markdown), cell);

            assertNotNull(cell.getRichStringCellValue());
            assertEquals("Cell with Bold and Italic\n", cell.getRichStringCellValue().getString());
            assertTrue(cell.getRichStringCellValue().numFormattingRuns() > 0);
        }
    }

    @Test
    public void testDisabledImage() {
        String markdown = "![Test Image](invalid-path.png)";
        XWPFDocument doc = new XWPFDocument();
        XWPFParagraph paragraph = doc.createParagraph();

        PoiRenderer disabledRenderer = PoiRenderer.builder()
                .handleImage(false)
                .build();
        disabledRenderer.render(parse(markdown), paragraph);

        assertEquals("[Image: invalid-path.png]", paragraph.getText());
    }

    @Test
    public void testGenerateDocx() throws IOException {
        String markdown = "# Commonmark POI Renderer Output Sample\n\n" +
                "This document is dynamically generated from **Markdown** using the new unified `PoiRenderer` class.\n\n" +
                "## Formatting Showcase\n\n" +
                "We support common formatting options:\n" +
                "- **Bold text** to emphasize key statements.\n" +
                "- *Italic text* for styling annotations.\n" +
                "- Inline `monospaced code` blocks for technical terms.\n" +
                "- [ProjectUnified](https://github.com/ProjectUnified) hyperlinks.\n\n" +
                "## Nested Structure Demo\n\n" +
                "> Blockquotes are indented by default and styled as italic body text.\n\n" +
                "Ordered lists are also correctly styled:\n" +
                "1. First item\n" +
                "2. Second item\n" +
                "   - Nested item A\n" +
                "   - Nested item B\n" +
                "3. Third item\n\n" +
                "### Embedded Graphics\n\n" +
                "Below is the dynamically resolved logo graphic:\n\n" +
                "![Logo](https://avatars.githubusercontent.com/u/141514393?s=200&v=4)\n";

        XWPFDocument doc = new XWPFDocument();
        renderer.render(parse(markdown), doc);

        java.io.File targetDir = new java.io.File("target");
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream("target/output.docx")) {
            doc.write(fos);
        }
    }
}
