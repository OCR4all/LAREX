package de.uniwue.zpd.dachs.larex.backend.repository.page;

import de.uniwue.zpd.dachs.larex.backend.entity.PageWorkflowEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PageWorkflowEventRepository extends JpaRepository<PageWorkflowEvent, String> {

    List<PageWorkflowEvent> findByPageIdOrderByCreatedDesc(String pageId);
}
