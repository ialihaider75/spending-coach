package com.intellermatrix.spending_coach.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCatalogTest {

    @Test
    void idIsTheLowercaseSnakeCaseToolName() {
        assertThat(ToolCatalog.GET_OVERVIEW.id()).isEqualTo("get_overview");
        assertThat(ToolCatalog.SPENDING_BY_CATEGORY.id()).isEqualTo("spending_by_category");
    }

    @Test
    void definitionIsNonBlankForEveryTool() {
        for (ToolCatalog tool : ToolCatalog.values()) {
            assertThat(tool.definition()).isNotBlank();
        }
    }
}
