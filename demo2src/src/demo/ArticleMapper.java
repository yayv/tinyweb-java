package demo;

import org.apache.ibatis.annotations.*;
import java.util.List;

public interface ArticleMapper {
    @Select("SELECT * FROM article WHERE id = #{id}")
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "title", column = "title"),
        @Result(property = "content", column = "content"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at")
    })
    Article selectById(Integer id);

    @Select("SELECT * FROM article ORDER BY id DESC")
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "title", column = "title"),
        @Result(property = "content", column = "content"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at")
    })
    List<Article> selectAll();

    @Insert("INSERT INTO article (title, content, created_at, updated_at) VALUES (#{title}, #{content}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Article article);

    @Update("UPDATE article SET title = #{title}, content = #{content}, updated_at = #{updatedAt} WHERE id = #{id}")
    void update(Article article);

    @Delete("DELETE FROM article WHERE id = #{id}")
    void deleteById(Integer id);
}
