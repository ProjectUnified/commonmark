package io.github.projectunified.commonmark.bbcode;

import org.commonmark.node.*;
import org.commonmark.renderer.Renderer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class BbcodeRenderer implements Renderer {
    private final Map<Integer, String> headingSizes;
    private final String thematicBreakTag;

    private BbcodeRenderer(Builder builder) {
        this.headingSizes = new HashMap<>(builder.headingSizes);
        this.thematicBreakTag = builder.thematicBreakTag;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void render(Node node, Appendable output) {
        if (node == null) {
            throw new IllegalArgumentException("Node must not be null");
        }
        if (output == null) {
            throw new IllegalArgumentException("Output must not be null");
        }
        node.accept(new BbcodeVisitor(output));
    }

    @Override
    public String render(Node node) {
        if (node == null) {
            throw new IllegalArgumentException("Node must not be null");
        }
        StringBuilder sb = new StringBuilder();
        render(node, sb);
        return sb.toString();
    }

    public static class Builder {
        private final Map<Integer, String> headingSizes = defaultHeadingSizes();
        private String thematicBreakTag = "[hr]";

        private static Map<Integer, String> defaultHeadingSizes() {
            Map<Integer, String> map = new HashMap<>();
            map.put(1, "6");
            map.put(2, "5");
            map.put(3, "4");
            map.put(4, "3");
            map.put(5, "2");
            map.put(6, "1");
            return map;
        }

        public Builder headingSize(int level, String size) {
            if (size == null) {
                this.headingSizes.remove(level);
            } else {
                this.headingSizes.put(level, size);
            }
            return this;
        }

        public Builder headingSizes(Map<Integer, String> headingSizes) {
            this.headingSizes.clear();
            if (headingSizes != null) {
                this.headingSizes.putAll(headingSizes);
            }
            return this;
        }

        public Builder thematicBreakTag(String tag) {
            this.thematicBreakTag = tag;
            return this;
        }

        public BbcodeRenderer build() {
            return new BbcodeRenderer(this);
        }
    }

    private static class BbcodeWriter {
        private final Appendable appendable;
        private int consecutiveNewlines = 2; // Treat start of document as preceded by newlines

        BbcodeWriter(Appendable appendable) {
            this.appendable = appendable;
        }

        void write(String s) {
            if (s == null || s.isEmpty()) {
                return;
            }
            try {
                appendable.append(s);
                for (int i = 0; i < s.length(); i++) {
                    if (s.charAt(i) == '\n') {
                        consecutiveNewlines++;
                    } else {
                        consecutiveNewlines = 0;
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        void ensureNewlines(int count) {
            while (consecutiveNewlines < count) {
                write("\n");
            }
        }
    }

    private class BbcodeVisitor extends AbstractVisitor {
        private final BbcodeWriter writer;

        BbcodeVisitor(Appendable appendable) {
            this.writer = new BbcodeWriter(appendable);
        }


        @Override
        public void visit(Paragraph paragraph) {
            Node parent = paragraph.getParent();
            boolean inListItem = parent instanceof ListItem;
            boolean isFirstInContainer = (inListItem || parent instanceof BlockQuote)
                    && parent.getFirstChild() == paragraph;

            if (!isFirstInContainer) {
                writer.ensureNewlines(2);
            }
            visitChildren(paragraph);
            if (inListItem) {
                if (paragraph.getNext() != null) {
                    writer.ensureNewlines(2);
                }
            } else {
                if (paragraph.getNext() != null) {
                    writer.ensureNewlines(2);
                } else {
                    writer.ensureNewlines(1);
                }
            }
        }

        @Override
        public void visit(Heading heading) {
            writer.ensureNewlines(2);
            int level = heading.getLevel();
            String size = headingSizes.get(level);
            if (size != null && !size.isEmpty()) {
                writer.write("[size=" + size + "]");
            }
            writer.write("[b]");
            visitChildren(heading);
            writer.write("[/b]");
            if (size != null && !size.isEmpty()) {
                writer.write("[/size]");
            }
            if (heading.getNext() != null) {
                writer.ensureNewlines(2);
            } else {
                writer.ensureNewlines(1);
            }
        }

        @Override
        public void visit(BlockQuote blockQuote) {
            writer.ensureNewlines(2);
            writer.write("[quote]");
            writer.ensureNewlines(1);
            visitChildren(blockQuote);
            writer.ensureNewlines(1);
            writer.write("[/quote]");
            if (blockQuote.getNext() != null) {
                writer.ensureNewlines(2);
            } else {
                writer.ensureNewlines(1);
            }
        }

        @Override
        public void visit(BulletList bulletList) {
            writer.ensureNewlines(2);
            writer.write("[list]");
            writer.ensureNewlines(1);
            visitChildren(bulletList);
            writer.ensureNewlines(1);
            writer.write("[/list]");
            if (bulletList.getNext() != null) {
                writer.ensureNewlines(2);
            } else {
                writer.ensureNewlines(1);
            }
        }

        @Override
        public void visit(OrderedList orderedList) {
            writer.ensureNewlines(2);
            Integer markerStartNumber = orderedList.getMarkerStartNumber();
            int start = markerStartNumber != null ? markerStartNumber : 1;
            if (start == 1) {
                writer.write("[list=1]");
            } else {
                writer.write("[list=" + start + "]");
            }
            writer.ensureNewlines(1);
            visitChildren(orderedList);
            writer.ensureNewlines(1);
            writer.write("[/list]");
            if (orderedList.getNext() != null) {
                writer.ensureNewlines(2);
            } else {
                writer.ensureNewlines(1);
            }
        }

        @Override
        public void visit(ListItem listItem) {
            writer.ensureNewlines(1);
            writer.write("[*]");
            visitChildren(listItem);
        }

        @Override
        public void visit(ThematicBreak thematicBreak) {
            writer.ensureNewlines(2);
            if (thematicBreakTag != null && !thematicBreakTag.isEmpty()) {
                writer.write(thematicBreakTag);
            }
            if (thematicBreak.getNext() != null) {
                writer.ensureNewlines(2);
            } else {
                writer.ensureNewlines(1);
            }
        }

        @Override
        public void visit(FencedCodeBlock fencedCodeBlock) {
            writer.ensureNewlines(2);
            writer.write("[code]");
            writer.write(fencedCodeBlock.getLiteral());
            writer.write("[/code]");
            if (fencedCodeBlock.getNext() != null) {
                writer.ensureNewlines(2);
            } else {
                writer.ensureNewlines(1);
            }
        }

        @Override
        public void visit(IndentedCodeBlock indentedCodeBlock) {
            writer.ensureNewlines(2);
            writer.write("[code]");
            writer.write(indentedCodeBlock.getLiteral());
            writer.write("[/code]");
            if (indentedCodeBlock.getNext() != null) {
                writer.ensureNewlines(2);
            } else {
                writer.ensureNewlines(1);
            }
        }

        @Override
        public void visit(Text text) {
            writer.write(text.getLiteral());
        }

        @Override
        public void visit(Emphasis emphasis) {
            writer.write("[i]");
            visitChildren(emphasis);
            writer.write("[/i]");
        }

        @Override
        public void visit(StrongEmphasis strongEmphasis) {
            writer.write("[b]");
            visitChildren(strongEmphasis);
            writer.write("[/b]");
        }

        @Override
        public void visit(Code codeNode) {
            writer.write("[code]");
            writer.write(codeNode.getLiteral());
            writer.write("[/code]");
        }

        @Override
        public void visit(Link link) {
            writer.write("[url=" + link.getDestination() + "]");
            visitChildren(link);
            writer.write("[/url]");
        }

        @Override
        public void visit(Image image) {
            writer.write("[img]" + image.getDestination() + "[/img]");
        }

        @Override
        public void visit(HardLineBreak hardLineBreak) {
            writer.write("\n");
        }

        @Override
        public void visit(SoftLineBreak softLineBreak) {
            writer.write("\n");
        }

        @Override
        public void visit(CustomNode customNode) {
            if (customNode.getClass().getName().equals("org.commonmark.ext.gfm.strikethrough.Strikethrough")) {
                writer.write("[s]");
                visitChildren(customNode);
                writer.write("[/s]");
            } else {
                visitChildren(customNode);
            }
        }
    }
}
