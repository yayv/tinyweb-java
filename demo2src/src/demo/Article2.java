package demo;

import top.x0a.tinyweb.Controller;
import org.apache.ibatis.session.SqlSession;
import java.util.List;

public class Article2 extends Controller {

    @Override
    public void index() {
        echo("<h1>Article Controller</h1><p>CMS API endpoints</p>");
    }

    public void list() {
        SqlSession session = MyBatisUtil.getSqlSession();
        try {
            ArticleMapper mapper = session.getMapper(ArticleMapper.class);
            List<Article> articles = mapper.selectAll();
            json(toJsonArray(articles));
        } finally {
            session.close();
        }
    }

    public void get() {
        String idStr = ctx.param("id");
        if (idStr == null || idStr.isEmpty()) {
            idStr = ctx.param("params2");
        }
        if (idStr == null || idStr.isEmpty()) {
            status(400);
            json("{\"ok\":false,\"message\":\"missing id\"}");
            return;
        }

        try {
            Integer id = Integer.parseInt(idStr);
            SqlSession session = MyBatisUtil.getSqlSession();
            try {
                ArticleMapper mapper = session.getMapper(ArticleMapper.class);
                Article article = mapper.selectById(id);
                if (article == null) {
                    status(404);
                    json("{\"ok\":false,\"message\":\"article not found\"}");
                } else {
                    json(toJson(article));
                }
            } finally {
                session.close();
            }
        } catch (NumberFormatException e) {
            status(400);
            json("{\"ok\":false,\"message\":\"invalid id\"}");
        }
    }

    public void create() {
        String title = ctx.form("title");
        String content = ctx.form("content");

        if (title == null || title.isEmpty() || content == null || content.isEmpty()) {
            status(400);
            json("{\"ok\":false,\"message\":\"missing title or content\"}");
            return;
        }

        Article article = new Article(title, content);
        SqlSession session = MyBatisUtil.getSqlSession();
        try {
            ArticleMapper mapper = session.getMapper(ArticleMapper.class);
            mapper.insert(article);
            session.commit();
            status(201);
            json(toJson(article));
        } finally {
            session.close();
        }
    }

    public void update() {
        String idStr = ctx.param("id");
        if (idStr == null || idStr.isEmpty()) {
            idStr = ctx.param("params2");
        }
        String title = ctx.form("title");
        String content = ctx.form("content");

        if (idStr == null || idStr.isEmpty()) {
            status(400);
            json("{\"ok\":false,\"message\":\"missing id\"}");
            return;
        }

        try {
            Integer id = Integer.parseInt(idStr);
            SqlSession session = MyBatisUtil.getSqlSession();
            try {
                ArticleMapper mapper = session.getMapper(ArticleMapper.class);
                Article article = mapper.selectById(id);
                if (article == null) {
                    status(404);
                    json("{\"ok\":false,\"message\":\"article not found\"}");
                    return;
                }

                if (title != null && !title.isEmpty()) {
                    article.setTitle(title);
                }
                if (content != null && !content.isEmpty()) {
                    article.setContent(content);
                }
                article.setUpdatedAt(System.currentTimeMillis());

                mapper.update(article);
                session.commit();
                json(toJson(article));
            } finally {
                session.close();
            }
        } catch (NumberFormatException e) {
            status(400);
            json("{\"ok\":false,\"message\":\"invalid id\"}");
        }
    }

    public void delete() {
        String idStr = ctx.param("id");
        if (idStr == null || idStr.isEmpty()) {
            idStr = ctx.param("params2");
        }
        if (idStr == null || idStr.isEmpty()) {
            status(400);
            json("{\"ok\":false,\"message\":\"missing id\"}");
            return;
        }

        try {
            Integer id = Integer.parseInt(idStr);
            SqlSession session = MyBatisUtil.getSqlSession();
            try {
                ArticleMapper mapper = session.getMapper(ArticleMapper.class);
                Article article = mapper.selectById(id);
                if (article == null) {
                    status(404);
                    json("{\"ok\":false,\"message\":\"article not found\"}");
                    return;
                }

                mapper.deleteById(id);
                session.commit();
                json("{\"ok\":true,\"message\":\"deleted\"}");
            } finally {
                session.close();
            }
        } catch (NumberFormatException e) {
            status(400);
            json("{\"ok\":false,\"message\":\"invalid id\"}");
        }
    }

    private String toJson(Article article) {
        if (article == null) return "null";
        return "{\"id\":" + article.getId() +
               ",\"title\":\"" + escapeJson(article.getTitle()) + "\"" +
               ",\"content\":\"" + escapeJson(article.getContent()) + "\"" +
               ",\"createdAt\":" + article.getCreatedAt() +
               ",\"updatedAt\":" + article.getUpdatedAt() + "}";
    }

    private String toJsonArray(List<Article> articles) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < articles.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(toJson(articles.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
