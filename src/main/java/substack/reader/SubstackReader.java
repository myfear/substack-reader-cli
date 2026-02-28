package substack.reader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeVisitor;

import dev.tamboui.layout.Position;
import dev.tamboui.layout.Rect;
import dev.tamboui.layout.Size;
import dev.tamboui.style.Color;
import dev.tamboui.style.Modifier;
import dev.tamboui.style.Overflow;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.TuiRunner;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.list.ListState;
import dev.tamboui.widgets.list.ListWidget;
import dev.tamboui.widgets.paragraph.Paragraph;

/**
 * Terminal Substack reader using TamboUI.
 * Run with: mvn compile exec:java -q
 */
public class SubstackReader {

    // ── Data model ─────────────────────────────────────────────────────────────
    record Post(String title, String subtitle, String date, String url, String bodyHtml, boolean free) {
    }

    enum Screen {
        LIST, ARTICLE
    }

    // ── Entry point ────────────────────────────────────────────────────────────
    public static void main(String[] args) throws Exception {
        System.out.println("Fetching articles from The Main Thread...");
        List<Post> posts;
        try {
            posts = fetchPosts("https://www.the-main-thread.com", 25);
        } catch (Exception e) {
            System.err.println("Failed to fetch posts: " + e.getMessage());
            return;
        }

        if (posts.isEmpty()) {
            System.err.println("No posts found.");
            return;
        }

        var listItems = posts.stream()
                .map(p -> (p.free() ? "  " : "🔒 ") + "[" + p.date() + "] " + p.title())
                .toArray(String[]::new);

        var listState = new ListState();
        listState.select(0);

        var screen = new Screen[] { Screen.LIST };
        var scrollOff = new int[] { 0 };

        try (var tui = TuiRunner.create()) {
            tui.run(
                    (event, runner) -> switch (event) {

                        case KeyEvent k when k.isQuit() && screen[0] == Screen.LIST -> {
                            runner.quit();
                            yield true;
                        }
                        case KeyEvent k when (k.isCancel() || k.isQuit()) && screen[0] == Screen.ARTICLE -> {
                            screen[0] = Screen.LIST;
                            scrollOff[0] = 0;
                            yield true;
                        }
                        case KeyEvent k when k.isDown() && screen[0] == Screen.LIST -> {
                            listState.selectNext(listItems.length);
                            yield true;
                        }
                        case KeyEvent k when k.isUp() && screen[0] == Screen.LIST -> {
                            listState.selectPrevious();
                            yield true;
                        }
                        case KeyEvent k when k.isSelect() && screen[0] == Screen.LIST -> {
                            screen[0] = Screen.ARTICLE;
                            scrollOff[0] = 0;
                            yield true;
                        }
                        case KeyEvent k when k.isDown() && screen[0] == Screen.ARTICLE -> {
                            scrollOff[0]++;
                            yield true;
                        }
                        case KeyEvent k when k.isUp() && screen[0] == Screen.ARTICLE -> {
                            scrollOff[0] = Math.max(0, scrollOff[0] - 1);
                            yield true;
                        }
                        default -> false;
                    },

                    frame -> {
                        var area = frame.area();
                        if (screen[0] == Screen.LIST) {
                            renderList(frame, area, listItems, listState, posts);
                        } else {
                            int idx = Optional.ofNullable(listState.selected()).orElse(0);
                            renderArticle(frame, area, posts.get(idx), scrollOff[0]);
                        }
                    });
        }
    }

    // ── Render: list screen ─────────────────────────────────────────────────────
    static void renderList(Frame frame, Rect area,
            String[] items, ListState state, List<Post> posts) {
        var outerBlock = Block.builder()
                .title(Title.from(" 📰  The Main Thread — Substack Reader "))
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .build();
        var inner = outerBlock.inner(area);
        frame.renderWidget(outerBlock, area);

        var listWidget = ListWidget.builder()
                .items(items)
                .highlightStyle(Style.EMPTY.fg(Color.CYAN).addModifier(Modifier.BOLD))
                .highlightSymbol("▶ ")
                .build();
        frame.renderStatefulWidget(listWidget, inner, state);

        renderStatusBar(frame, area, "↑↓ navigate   Enter read   q quit");
    }

    // ── Render: article screen ──────────────────────────────────────────────────
    static void renderArticle(Frame frame, Rect area, Post post, int scrollOffset) {
        // Split manually to avoid Layout cache reusing solver (DuplicateConstraintException)
        var headerRect = Rect.of(new Position(area.x(), area.y()), new Size(area.width(), 5));
        var bodyRect = Rect.of(
                new Position(area.x(), area.y() + 5),
                new Size(area.width(), Math.max(0, area.height() - 5)));

        // Header
        var hBlock = Block.builder()
                .title(Title.from(" 📖 " + truncate(post.title(), 58) + " "))
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .build();
        frame.renderWidget(hBlock, headerRect);
        var hInner = hBlock.inner(headerRect);
        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(
                                Line.from(Span.styled(post.date() + (post.free() ? "  🟢 free" : "  🔒 paid"),
                                        Style.EMPTY.fg(Color.YELLOW))),
                                Line.from(Span.raw(post.subtitle())),
                                Line.from(Span.styled(post.url(), Style.EMPTY.fg(Color.BLUE).addModifier(Modifier.DIM)))))
                        .build(),
                hInner);

        // Body
        var bBlock = Block.builder()
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .build();
        var bInner = bBlock.inner(bodyRect);
        frame.renderWidget(bBlock, bodyRect);
        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(htmlToText(post.bodyHtml())))
                        .scroll(scrollOffset)
                        .overflow(Overflow.WRAP_WORD)
                        .build(),
                bInner);

        renderStatusBar(frame, area, "↑↓ scroll   Esc / q back to list");
    }

    static void renderStatusBar(Frame frame, Rect area, String hint) {
        var barArea = Rect.of(new Position(area.x(), area.y() + area.height() - 1), new Size(area.width(), 1));
        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from("  " + hint))
                        .style(Style.EMPTY.bg(Color.DARK_GRAY).fg(Color.WHITE))
                        .build(),
                barArea);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────
    static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    static String htmlToText(String html) {
        if (html == null || html.isBlank())
            return "(No content — this article may be paywalled)\n\nVisit the article URL above to read it in your browser.";
        var out = new StringBuilder();
        var body = Jsoup.parse(html).body();
        if (body == null) return "";
        body.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof TextNode tn) {
                    out.append(tn.getWholeText());
                    return;
                }
                if (node instanceof Element el) {
                    var name = el.normalName();
                    switch (name) {
                        case "h1", "h2", "h3", "h4", "h5", "h6" -> out.append("\n\n## ");
                        case "p", "div", "li" -> out.append("\n");
                        case "br" -> out.append("\n");
                        case "pre" -> out.append("\n```\n");
                        default -> { }
                    }
                }
            }
            @Override
            public void tail(Node node, int depth) {
                if (node instanceof Element el) {
                    var name = el.normalName();
                    switch (name) {
                        case "h1", "h2", "h3", "h4", "h5", "h6" -> out.append("\n");
                        case "pre" -> out.append("\n```\n");
                        default -> { }
                    }
                }
            }
        });
        return out.toString().replaceAll("(\n\\s*){3,}", "\n\n").trim();
    }

    // ── HTTP + JSON ──────────────────────────────────────────────────────────────
    static List<Post> fetchPosts(String baseUrl, int limit) throws Exception {
        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/posts?limit=" + limit + "&offset=0&sort=new"))
                .header("User-Agent", "Mozilla/5.0 TamboUI-Demo/1.0")
                .GET().build();

        var resp = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200)
            throw new RuntimeException("HTTP " + resp.statusCode() + " from Substack API");

        return parsePostsJson(resp.body(), baseUrl);
    }

    static List<Post> parsePostsJson(String json, String baseUrl) {
        var posts = new ArrayList<Post>();
        var root = new Gson().fromJson(json, JsonElement.class);
        if (root == null) return posts;
        JsonArray arr;
        if (root.isJsonArray()) {
            arr = root.getAsJsonArray();
        } else {
            var postsEl = root.getAsJsonObject().get("posts");
            if (postsEl == null || !postsEl.isJsonArray()) return posts;
            arr = postsEl.getAsJsonArray();
        }
        for (JsonElement el : arr) {
            JsonObject obj = el.getAsJsonObject();
            var title = getStr(obj, "title");
            if (title == null || title.isBlank()) continue;
            var subtitle = getStr(obj, "subtitle");
            var date = getStr(obj, "post_date");
            var slug = getStr(obj, "slug");
            var body = getStr(obj, "body_html");
            var audience = getStr(obj, "audience");
            var d = date != null && date.length() >= 10 ? date.substring(0, 10) : "";
            posts.add(new Post(
                    title,
                    subtitle != null ? subtitle : "",
                    d,
                    baseUrl + "/p/" + (slug != null ? slug : ""),
                    body != null ? body : "",
                    "everyone".equals(audience)));
        }
        return posts;
    }

    static String getStr(JsonObject obj, String key) {
        if (!obj.has(key)) return null;
        var el = obj.get(key);
        return el.isJsonNull() ? null : el.getAsString();
    }
}
