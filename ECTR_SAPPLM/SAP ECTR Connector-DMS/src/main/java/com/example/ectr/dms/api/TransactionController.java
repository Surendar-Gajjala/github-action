// VERIFIED: Spring MVC REST controller.
package com.example.ectr.dms.api;

import com.example.ectr.dms.api.dto.Responses.TransactionResponse;
import com.example.ectr.dms.service.DocumentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/v1/transactions/{id}: state of a create request, used after a timeout or for monitoring. */
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final DocumentService documents;

    public TransactionController(DocumentService documents) {
        this.documents = documents;
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@PathVariable String id) {
        return documents.transaction(id).map(TransactionResponse::of)
                .orElseThrow(() -> new NotFoundException("Transaction " + id + " not found"));
    }
}
