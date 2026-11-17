package com.intellermatrix.spending_coach.agent.provider;

import com.intellermatrix.spending_coach.agent.dto.ProviderRequest;
import com.intellermatrix.spending_coach.agent.dto.ProviderResponse;

public interface LlmProvider {

    String name();

    ProviderResponse chat(ProviderRequest request);
}
