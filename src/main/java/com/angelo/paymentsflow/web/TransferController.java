package com.angelo.paymentsflow.web;

import com.angelo.paymentsflow.service.TransferCommand;
import com.angelo.paymentsflow.service.TransferOutcome;
import com.angelo.paymentsflow.service.TransferService;
import com.angelo.paymentsflow.web.dto.CreateTransferRequest;
import com.angelo.paymentsflow.web.dto.TransferResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    private final TransferService transfers;

    public TransferController(TransferService transfers) {
        this.transfers = transfers;
    }

    /**
     * Posts a transfer. Supply an {@code Idempotency-Key} header to make retries
     * safe: replaying the same key returns the original transfer (HTTP 200)
     * instead of posting again; a first-time request returns HTTP 201.
     */
    @PostMapping
    public ResponseEntity<TransferResponse> transfer(
            @Valid @RequestBody CreateTransferRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        TransferCommand command = new TransferCommand(
                request.fromAccountId(),
                request.toAccountId(),
                request.amount(),
                request.currency(),
                idempotencyKey);

        TransferOutcome outcome = transfers.transfer(command);
        TransferResponse body = TransferResponse.of(transfers.getTransfer(outcome.journalEntryId()));

        if (outcome.replayed()) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity
                .created(URI.create("/transfers/" + body.id()))
                .body(body);
    }

    @GetMapping("/{id}")
    public TransferResponse get(@PathVariable Long id) {
        return TransferResponse.of(transfers.getTransfer(id));
    }
}
