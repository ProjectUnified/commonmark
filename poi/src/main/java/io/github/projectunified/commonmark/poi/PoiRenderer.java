package io.github.projectunified.commonmark.poi;

import org.apache.poi.hssf.usermodel.HSSFRichTextString;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.RichTextString;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.util.Units;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xwpf.usermodel.*;
import org.commonmark.node.*;
import org.commonmark.node.Document;
import org.commonmark.renderer.Renderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.*;

public class PoiRenderer implements Renderer {
    private final String bodyFontFamily;
    private final double bodyFontSize;
    private final String codeFontFamily;
    private final double codeFontSize;
    private final int blockQuoteIndent;
    private final List<String> bulletListSymbols;
    private final String linkColor;
    private final boolean linkUnderline;
    private final List<Double> headingSizes;
    private final Workbook workbook;
    private final boolean handleImage;

    private PoiRenderer(Builder builder) {
        this.bodyFontFamily = builder.bodyFontFamily;
        this.bodyFontSize = builder.bodyFontSize;
        this.codeFontFamily = builder.codeFontFamily;
        this.codeFontSize = builder.codeFontSize;
        this.blockQuoteIndent = builder.blockQuoteIndent;
        this.bulletListSymbols = new ArrayList<>(builder.bulletListSymbols);
        this.linkColor = builder.linkColor;
        this.linkUnderline = builder.linkUnderline;
        this.headingSizes = new ArrayList<>(builder.headingSizes);
        this.workbook = builder.workbook;
        this.handleImage = builder.handleImage;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Renders the given markdown node tree to the specified Apache POI target object.
     * Supported targets:
     * - {@link org.apache.poi.xwpf.usermodel.IBody} (e.g., XWPFDocument, XWPFTableCell)
     * - {@link org.apache.poi.xwpf.usermodel.XWPFParagraph}
     * - {@link org.apache.poi.ss.usermodel.Cell}
     * - {@link org.apache.poi.ss.usermodel.RichTextString}
     *
     * @param node   the root commonmark node
     * @param target the Apache POI target component
     */
    public void render(Node node, Object target) {
        if (node == null) {
            throw new IllegalArgumentException("Node must not be null");
        }
        if (target == null) {
            throw new IllegalArgumentException("Target must not be null");
        }
        node.accept(new PoiVisitor(target));
    }

    @Override
    public void render(Node node, Appendable output) {
        throw new UnsupportedOperationException("Use render(Node, Object) to render to an Apache POI target");
    }

    @Override
    public String render(Node node) {
        throw new UnsupportedOperationException("Use render(Node, Object) to render to an Apache POI target");
    }

    public static class Builder {
        private String bodyFontFamily = "Calibri";
        private double bodyFontSize = 11.0;
        private String codeFontFamily = "Courier New";
        private double codeFontSize = 10.0;
        private int blockQuoteIndent = 720;
        private List<String> bulletListSymbols = Arrays.asList("• ", "◦ ", "▪ ");
        private String linkColor = "0000FF";
        private boolean linkUnderline = true;
        private List<Double> headingSizes = Arrays.asList(20.0, 16.0, 14.0, 12.0, 11.0, 11.0);
        private Workbook workbook;
        private boolean handleImage = true;

        public Builder handleImage(boolean handleImage) {
            this.handleImage = handleImage;
            return this;
        }

        public Builder workbook(Workbook workbook) {
            this.workbook = workbook;
            return this;
        }

        public Builder bodyFontFamily(String family) {
            this.bodyFontFamily = family;
            return this;
        }

        public Builder bodyFontSize(double size) {
            this.bodyFontSize = size;
            return this;
        }

        public Builder codeFontFamily(String family) {
            this.codeFontFamily = family;
            return this;
        }

        public Builder codeFontSize(double size) {
            this.codeFontSize = size;
            return this;
        }

        public Builder blockQuoteIndent(int indent) {
            this.blockQuoteIndent = indent;
            return this;
        }

        public Builder bulletListSymbols(List<String> symbols) {
            this.bulletListSymbols = symbols;
            return this;
        }

        public Builder linkColor(String color) {
            this.linkColor = color;
            return this;
        }

        public Builder linkUnderline(boolean underline) {
            this.linkUnderline = underline;
            return this;
        }

        public Builder headingSizes(List<Double> sizes) {
            this.headingSizes = sizes;
            return this;
        }

        public PoiRenderer build() {
            return new PoiRenderer(this);
        }
    }

    private static class ListState {
        final boolean ordered;
        final int level;
        int currentIndex = 1;

        ListState(boolean ordered, int level) {
            this.ordered = ordered;
            this.level = level;
        }
    }

    private static class RichTextSegment {
        final String text;
        final boolean bold;
        final boolean italic;
        final boolean code;
        final String linkUrl;

        RichTextSegment(String text, boolean bold, boolean italic, boolean code, String linkUrl) {
            this.text = text;
            this.bold = bold;
            this.italic = italic;
            this.code = code;
            this.linkUrl = linkUrl;
        }
    }

    private class PoiVisitor extends AbstractVisitor {
        private final Object target;
        private final Deque<ListState> listStates = new ArrayDeque<>();
        private final List<RichTextSegment> richTextSegments = new ArrayList<>();
        private final Map<String, Font> fontCache = new HashMap<>();
        private XWPFParagraph currentParagraph;
        // Style states
        private boolean bold = false;
        private boolean italic = false;
        private boolean code = false;
        private String currentLinkUrl = null;
        private int headingLevel = 0;
        private boolean inBlockQuote = false;

        PoiVisitor(Object target) {
            this.target = target;
            if (target instanceof XWPFParagraph) {
                this.currentParagraph = (XWPFParagraph) target;
            }
        }

        @Override
        public void visit(Document document) {
            visitChildren(document);
            if (target instanceof Cell) {
                applyCellRichText((Cell) target);
            } else if (target instanceof RichTextString) {
                applyRichTextSegments((RichTextString) target);
            }
        }

        @Override
        public void visit(Paragraph paragraph) {
            if (target instanceof IBody) {
                IBody body = (IBody) target;
                boolean createdNew = false;

                if (currentParagraph == null) {
                    currentParagraph = createParagraph(body);
                    createdNew = true;
                    currentParagraph.setSpacingAfter(120); // 6pt
                    if (inBlockQuote) {
                        currentParagraph.setIndentationLeft(blockQuoteIndent);
                    }
                }

                visitChildren(paragraph);

                if (createdNew) {
                    currentParagraph = null;
                }
            } else if (target instanceof XWPFParagraph) {
                visitChildren(paragraph);
            } else {
                visitChildren(paragraph);
                richTextSegments.add(new RichTextSegment("\n", false, false, false, null));
            }
        }

        @Override
        public void visit(Heading heading) {
            if (target instanceof IBody) {
                IBody body = (IBody) target;
                XWPFParagraph oldParagraph = currentParagraph;

                currentParagraph = createParagraph(body);
                currentParagraph.setSpacingBefore(240); // 12pt
                currentParagraph.setSpacingAfter(120);  // 6pt

                headingLevel = heading.getLevel();
                visitChildren(heading);
                headingLevel = 0;

                currentParagraph = oldParagraph;
            } else if (target instanceof XWPFParagraph) {
                headingLevel = heading.getLevel();
                visitChildren(heading);
                headingLevel = 0;
            } else {
                visitChildren(heading);
                richTextSegments.add(new RichTextSegment("\n", false, false, false, null));
            }
        }

        @Override
        public void visit(BlockQuote blockQuote) {
            if (target instanceof IBody) {
                boolean oldInBlockQuote = inBlockQuote;
                inBlockQuote = true;
                visitChildren(blockQuote);
                inBlockQuote = oldInBlockQuote;
            } else {
                visitChildren(blockQuote);
            }
        }

        @Override
        public void visit(BulletList bulletList) {
            listStates.push(new ListState(false, listStates.size() + 1));
            visitChildren(bulletList);
            listStates.pop();
        }

        @Override
        public void visit(OrderedList orderedList) {
            listStates.push(new ListState(true, listStates.size() + 1));
            visitChildren(orderedList);
            listStates.pop();
        }

        @Override
        public void visit(ListItem listItem) {
            if (target instanceof IBody) {
                IBody body = (IBody) target;
                ListState listState = listStates.peek();
                if (listState != null) {
                    XWPFParagraph oldParagraph = currentParagraph;
                    currentParagraph = createParagraph(body);
                    currentParagraph.setIndentationLeft(listState.level * 360);

                    XWPFRun prefixRun = currentParagraph.createRun();
                    prefixRun.setFontFamily(bodyFontFamily);
                    prefixRun.setFontSize(bodyFontSize);
                    if (listState.ordered) {
                        prefixRun.setText(listState.currentIndex + ". ");
                        listState.currentIndex++;
                    } else {
                        int symbolIdx = (listState.level - 1) % bulletListSymbols.size();
                        prefixRun.setText(bulletListSymbols.get(symbolIdx));
                    }

                    visitChildren(listItem);
                    currentParagraph = oldParagraph;
                } else {
                    visitChildren(listItem);
                }
            } else {
                visitChildren(listItem);
            }
        }

        @Override
        public void visit(ThematicBreak thematicBreak) {
            if (target instanceof IBody) {
                IBody body = (IBody) target;
                XWPFParagraph p = createParagraph(body);
                p.setBorderBottom(Borders.SINGLE);
            } else if (target instanceof RichTextString) {
                richTextSegments.add(new RichTextSegment("\n---\n", false, false, false, null));
            }
        }

        @Override
        public void visit(FencedCodeBlock fencedCodeBlock) {
            renderCodeBlock(fencedCodeBlock.getLiteral());
        }

        @Override
        public void visit(IndentedCodeBlock indentedCodeBlock) {
            renderCodeBlock(indentedCodeBlock.getLiteral());
        }

        private void renderCodeBlock(String literal) {
            if (target instanceof IBody) {
                IBody body = (IBody) target;
                XWPFParagraph p = createParagraph(body);
                p.setIndentationLeft(720);
                XWPFRun run = p.createRun();
                run.setFontFamily(codeFontFamily);
                run.setFontSize(codeFontSize);
                run.setText(literal.trim());
            } else if (target instanceof XWPFParagraph) {
                XWPFParagraph p = (XWPFParagraph) target;
                XWPFRun run = p.createRun();
                run.setFontFamily(codeFontFamily);
                run.setFontSize(codeFontSize);
                run.setText(literal.trim());
            } else {
                richTextSegments.add(new RichTextSegment(literal.trim() + "\n", false, false, true, null));
            }
        }

        @Override
        public void visit(Text text) {
            String literal = text.getLiteral();
            if (target instanceof IBody || target instanceof XWPFParagraph) {
                if (currentParagraph != null) {
                    XWPFRun run;
                    if (currentLinkUrl != null) {
                        try {
                            run = currentParagraph.createHyperlinkRun(currentLinkUrl);
                        } catch (Exception e) {
                            run = currentParagraph.createRun();
                            run.setUnderline(linkUnderline ? UnderlinePatterns.SINGLE : UnderlinePatterns.NONE);
                        }
                        run.setColor(linkColor);
                    } else {
                        run = currentParagraph.createRun();
                    }

                    run.setBold(bold || headingLevel > 0);
                    run.setItalic(italic || inBlockQuote);

                    if (code) {
                        run.setFontFamily(codeFontFamily);
                        run.setFontSize(codeFontSize);
                    } else {
                        run.setFontFamily(bodyFontFamily);
                        double size = headingLevel > 0 ? getHeadingSize(headingLevel) : bodyFontSize;
                        run.setFontSize(size);
                    }
                    run.setText(literal);
                }
            } else {
                richTextSegments.add(new RichTextSegment(literal, bold, italic, code, currentLinkUrl));
            }
        }

        @Override
        public void visit(Emphasis emphasis) {
            boolean oldItalic = italic;
            italic = true;
            visitChildren(emphasis);
            italic = oldItalic;
        }

        @Override
        public void visit(StrongEmphasis strongEmphasis) {
            boolean oldBold = bold;
            bold = true;
            visitChildren(strongEmphasis);
            bold = oldBold;
        }

        @Override
        public void visit(Code codeNode) {
            boolean oldCode = code;
            code = true;
            String literal = codeNode.getLiteral();
            if (target instanceof IBody || target instanceof XWPFParagraph) {
                if (currentParagraph != null) {
                    XWPFRun run = currentParagraph.createRun();
                    run.setFontFamily(codeFontFamily);
                    run.setFontSize(codeFontSize);
                    run.setBold(bold);
                    run.setItalic(italic);
                    run.setText(literal);
                }
            } else {
                richTextSegments.add(new RichTextSegment(literal, bold, italic, true, currentLinkUrl));
            }
            code = oldCode;
        }

        @Override
        public void visit(Link link) {
            String oldLinkUrl = currentLinkUrl;
            currentLinkUrl = link.getDestination();
            visitChildren(link);
            currentLinkUrl = oldLinkUrl;
        }

        @Override
        public void visit(Image image) {
            String url = image.getDestination();
            if (!handleImage) {
                if (target instanceof IBody || target instanceof XWPFParagraph) {
                    if (currentParagraph != null) {
                        XWPFRun run = currentParagraph.createRun();
                        String fallbackText = image.getTitle() != null && !image.getTitle().isEmpty() ? image.getTitle() : url;
                        run.setText("[Image: " + fallbackText + "]");
                    }
                } else {
                    richTextSegments.add(new RichTextSegment("[Image: " + url + "]", false, false, false, null));
                }
                return;
            }
            if (target instanceof IBody || target instanceof XWPFParagraph) {
                if (currentParagraph != null) {
                    XWPFRun run = currentParagraph.createRun();
                    try {
                        try (InputStream is = openImageStream(url)) {
                            byte[] bytes = readAllBytes(is);
                            int pictureType = getPictureType(url, bytes);
                            if (pictureType != -1) {
                                BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
                                if (img != null) {
                                    int w = img.getWidth();
                                    int h = img.getHeight();
                                    if (w > 450) {
                                        h = (int) (h * (450.0 / w));
                                        w = 450;
                                    }
                                    run.addPicture(new ByteArrayInputStream(bytes), pictureType, url, Units.toEMU(w), Units.toEMU(h));
                                    return;
                                }
                            }
                        }
                    } catch (Exception e) {
                        // ignore and fallback
                    }
                    String fallbackText = image.getTitle() != null && !image.getTitle().isEmpty() ? image.getTitle() : url;
                    run.setText("[Image: " + fallbackText + "]");
                }
            } else {
                richTextSegments.add(new RichTextSegment("[Image: " + url + "]", false, false, false, null));
            }
        }

        @Override
        public void visit(HardLineBreak hardLineBreak) {
            addNewLine();
        }

        @Override
        public void visit(SoftLineBreak softLineBreak) {
            addNewLine();
        }

        private void addNewLine() {
            if (target instanceof IBody || target instanceof XWPFParagraph) {
                if (currentParagraph != null) {
                    XWPFRun run = currentParagraph.createRun();
                    run.addBreak();
                }
            } else {
                richTextSegments.add(new RichTextSegment("\n", false, false, false, null));
            }
        }

        private double getHeadingSize(int level) {
            if (level < 1) level = 1;
            if (level > headingSizes.size()) return headingSizes.get(headingSizes.size() - 1);
            return headingSizes.get(level - 1);
        }

        private XWPFParagraph createParagraph(IBody body) {
            if (body instanceof XWPFDocument) {
                return ((XWPFDocument) body).createParagraph();
            } else if (body instanceof XWPFTableCell) {
                return ((XWPFTableCell) body).addParagraph();
            } else if (body instanceof XWPFHeaderFooter) {
                return ((XWPFHeaderFooter) body).createParagraph();
            } else {
                throw new IllegalArgumentException("Unsupported IBody type: " + body.getClass().getName());
            }
        }

        private Font getOrCreateFont(Workbook wb, boolean bold, boolean italic, boolean code) {
            if (wb == null) return null;
            String key = bold + "-" + italic + "-" + code;
            return fontCache.computeIfAbsent(key, k -> {
                Font font = wb.createFont();
                font.setBold(bold);
                font.setItalic(italic);
                if (code) {
                    font.setFontName(codeFontFamily);
                    font.setFontHeightInPoints((short) codeFontSize);
                } else {
                    font.setFontName(bodyFontFamily);
                    font.setFontHeightInPoints((short) bodyFontSize);
                }
                return font;
            });
        }

        private void applyCellRichText(Cell cell) {
            Workbook wb = cell.getSheet().getWorkbook();
            StringBuilder fullText = new StringBuilder();
            for (RichTextSegment segment : richTextSegments) {
                fullText.append(segment.text);
            }

            RichTextString rt = wb.getCreationHelper().createRichTextString(fullText.toString());
            int currentIndex = 0;
            for (RichTextSegment segment : richTextSegments) {
                int len = segment.text.length();
                if (len > 0) {
                    if (segment.bold || segment.italic || segment.code) {
                        Font font = getOrCreateFont(wb, segment.bold, segment.italic, segment.code);
                        if (font != null) {
                            rt.applyFont(currentIndex, currentIndex + len, font);
                        }
                    }
                    currentIndex += len;
                }
            }
            cell.setCellValue(rt);
        }

        private void applyRichTextSegments(RichTextString richText) {
            Workbook wb = workbook;
            if (richText instanceof XSSFRichTextString) {
                XSSFRichTextString xssf = (XSSFRichTextString) richText;
                for (RichTextSegment segment : richTextSegments) {
                    Font font = getOrCreateFont(wb, segment.bold, segment.italic, segment.code);
                    if (font != null) {
                        xssf.append(segment.text, (org.apache.poi.xssf.usermodel.XSSFFont) font);
                    } else {
                        xssf.append(segment.text);
                    }
                }
            } else if (richText instanceof HSSFRichTextString) {
                HSSFRichTextString hssf = (HSSFRichTextString) richText;
                int currentIndex = 0;
                for (RichTextSegment segment : richTextSegments) {
                    int len = segment.text.length();
                    if (len > 0) {
                        if (segment.bold || segment.italic || segment.code) {
                            Font font = getOrCreateFont(wb, segment.bold, segment.italic, segment.code);
                            if (font != null && currentIndex + len <= hssf.length()) {
                                hssf.applyFont(currentIndex, currentIndex + len, font);
                            }
                        }
                        currentIndex += len;
                    }
                }
            }
        }

        private int getPictureType(String url, byte[] bytes) {
            int type = getPictureTypeFromUrl(url);
            if (type != -1) {
                return type;
            }
            try (InputStream is = new ByteArrayInputStream(bytes)) {
                String mimeType = URLConnection.guessContentTypeFromStream(is);
                if (mimeType != null) {
                    return getPictureTypeFromMime(mimeType);
                }
            } catch (Exception e) {
                // ignore
            }
            return -1;
        }

        private int getPictureTypeFromUrl(String url) {
            String lower = url.toLowerCase();
            if (lower.endsWith(".png")) return XWPFDocument.PICTURE_TYPE_PNG;
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return XWPFDocument.PICTURE_TYPE_JPEG;
            if (lower.endsWith(".gif")) return XWPFDocument.PICTURE_TYPE_GIF;
            if (lower.endsWith(".bmp")) return XWPFDocument.PICTURE_TYPE_BMP;
            return -1;
        }

        private int getPictureTypeFromMime(String mimeType) {
            String lower = mimeType.toLowerCase();
            if (lower.contains("png")) return XWPFDocument.PICTURE_TYPE_PNG;
            if (lower.contains("jpeg") || lower.contains("jpg")) return XWPFDocument.PICTURE_TYPE_JPEG;
            if (lower.contains("gif")) return XWPFDocument.PICTURE_TYPE_GIF;
            if (lower.contains("bmp")) return XWPFDocument.PICTURE_TYPE_BMP;
            return -1;
        }

        private InputStream openImageStream(String url) throws Exception {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                return new URL(url).openStream();
            }
            File file = new File(url);
            if (file.exists() && file.isFile()) {
                return Files.newInputStream(file.toPath());
            }
            InputStream is = getClass().getResourceAsStream(url);
            if (is == null) {
                is = Thread.currentThread().getContextClassLoader().getResourceAsStream(url);
            }
            if (is == null) {
                throw new FileNotFoundException("Image resource not found: " + url);
            }
            return is;
        }

        private byte[] readAllBytes(InputStream is) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[4096];
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            return buffer.toByteArray();
        }
    }
}
