package com.intellermatrix.spending_coach.agent.provider;

import com.intellermatrix.spending_coach.agent.dto.provider.ProviderRequest;
import com.intellermatrix.spending_coach.agent.dto.provider.ProviderResponse;

public interface LlmProvider {

    String name();

    ProviderResponse chat(ProviderRequest request);
}
