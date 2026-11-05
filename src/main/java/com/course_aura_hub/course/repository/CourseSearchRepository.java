package com.course_aura_hub.course.repository;

import com.course_aura_hub.course.model.Course;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Ranks catalog matches for a search. Full-text matches (stemmed, weighted by field) come first, then substring
 * matches on title, description, instructor and tags, which keeps partial words and non-English titles findable.
 */
@org.springframework.stereotype.Repository
public interface CourseSearchRepository extends Repository<Course, Integer> {

    /**
     * @param query   the raw search, parsed with websearch syntax ("quoted phrases", -excluded words, or)
     * @param pattern a lower-case LIKE pattern with wildcards already escaped
     */
    @Query(value = """
            select c.id
            from courses c
            where c.search_vector @@ websearch_to_tsquery('english', :query)
               or lower(c.title) like :pattern
               or lower(c.description) like :pattern
               or lower(c.instructor) like :pattern
               or exists (select 1 from course_tags t where t.course_id = c.id and lower(t.tag) like :pattern)
            order by
                ts_rank(c.search_vector, websearch_to_tsquery('english', :query))
                + case when lower(c.title) like :pattern then 1 else 0 end
                + case when exists (select 1 from course_tags t
                                    where t.course_id = c.id and lower(t.tag) = lower(trim(:query))) then 0.5 else 0 end
                desc,
                c.total_students desc,
                c.id
            limit 500
            """, nativeQuery = true)
    List<Integer> findRankedIds(@Param("query") String query, @Param("pattern") String pattern);
}
