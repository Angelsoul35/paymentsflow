package com.angelo.paymentsflow.web.dto;

import com.angelo.paymentsflow.domain.Direction;
import com.angelo.paymentsflow.domain.Posting;

/** API representation of a single posting leg. */
public record PostingView(Long accountId, String accountName, Direction direction, long amount) {

    public static PostingView of(Posting posting) {
        return new PostingView(
                posting.getAccount().getId(),
                posting.getAccount().getName(),
                posting.getDirection(),
                posting.getAmountMinor());
    }
}
