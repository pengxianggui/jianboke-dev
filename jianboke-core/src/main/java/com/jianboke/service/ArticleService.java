package com.jianboke.service;

import com.jianboke.domain.Article;
import com.jianboke.domain.criteria.ArticleCriteria;
import com.jianboke.repository.ArticleRepository;
import com.jianboke.utils.DBConfigUtils;
import org.hibernate.Criteria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * jdbc查询
 * Created by pengxg on 2017/3/25.
 */
@Component("ArticleService")
public class ArticleService {

    private static final Logger log = LoggerFactory.getLogger(ArticleService.class);

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Connection conn = null;
    private PreparedStatement stmt = null;
    private ResultSet rs = null;

    public List<Article> queryByCriteria(ArticleCriteria criteria) throws SQLException {
        List<Article> resultList;
        String findBy = criteria.getFindBy(),
               filter = criteria.getFilter();
        Integer page = criteria.getPage(),
                size = criteria.getSize(),
                begin = (page - 1) * size;
        DataSource ds = jdbcTemplate.getDataSource();
        Long authorId = userService.getUserWithAuthorities().getId();
        boolean isGroupByBook = false;
        try {
            conn = ds.getConnection();
            log.debug("Connection open");
            StringBuffer sb = new StringBuffer();
            sb.append("SELECT DISTINCT a.* FROM articles a ");
            if (findBy.equalsIgnoreCase("all")) {
                // 无需根据bookId查询
                sb.append("WHERE ");
            } else if (findBy.equalsIgnoreCase("alone")) {
                // 查询未归类的博客
                sb.append("WHERE ");
                sb.append("a.id NOT IN (SELECT DISTINCT article_id FROM book_chapter_articles) AND ");
            } else { // findBy的值是bookId
                isGroupByBook = true;
                sb.append(", book_chapter_articles bca WHERE ");
                sb.append("(bca.article_id = a.id) AND ");
                sb.append("bca.book_id = ? AND ");
            }
            sb.append("(a.author_id = ? OR a.second_author_id = ?) AND");
            sb.append("(a.title LIKE ? OR a.labels LIKE ?) ");
            sb.append("ORDER BY a.last_modified_date desc ");
            sb.append("LIMIT ?, ?");
            log.info("sql is :{}" , sb.toString());
            stmt = conn.prepareStatement(sb.toString());
            if (isGroupByBook) {
                stmt.setLong(1, Long.valueOf(findBy));
                stmt.setLong(2, authorId);
                stmt.setLong(3, authorId);
                stmt.setString(4, "%" + filter + "%");
                stmt.setString(5, "%" + filter + "%");
                stmt.setInt(6, begin);
                stmt.setInt(7, size);
            } else {
                stmt.setLong(1, authorId);
                stmt.setLong(2, authorId);
                stmt.setString(3, "%" + filter + "%");
                stmt.setString(4, "%" + filter + "%");
                stmt.setInt(5, begin);
                stmt.setInt(6, size);
            }
            rs = stmt.executeQuery();
            resultList = getResultFromRs(rs);
            return resultList;
        } catch (SQLException e) {
            if (log.isDebugEnabled()) {
                e.printStackTrace();
            }
            throw e;
        } finally {
            log.debug("closeConn");
            DBConfigUtils.closeConn(conn, stmt, rs);
        }
    }

    private List<Article> getResultFromRs(ResultSet rs) throws SQLException {
        List<Article> temp = new ArrayList<>();
        while (rs.next()) {
            Article a = new Article();
            a.setId(rs.getLong("id"));
            a.setTitle(rs.getString("title"));
            a.setContent(rs.getString("content"));
            a.setLabels(rs.getString("labels"));
            a.setAuthorId(rs.getLong("author_id"));
            a.setSecondAuthorId(rs.getLong("second_author_id"));
            temp.add(a);
            System.out.println(a.toString());
        }
        return temp;
    }

}