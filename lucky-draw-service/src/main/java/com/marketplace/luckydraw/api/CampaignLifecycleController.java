package com.marketplace.luckydraw.api;

import com.marketplace.luckydraw.domain.Campaign;
import com.marketplace.luckydraw.domain.Entry;
import com.marketplace.luckydraw.service.CampaignLifecycleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/campaigns/{id}")
public class CampaignLifecycleController {
    private final CampaignLifecycleService campaigns;
    public CampaignLifecycleController(CampaignLifecycleService campaigns) { this.campaigns = campaigns; }

    @PostMapping("/end")
    public Campaign end(@PathVariable String id, @RequestHeader("X-Demo-User") String sellerId,
            @RequestHeader("X-Demo-Role") String role) {
        DemoAuth.require(role, "SELLER");
        return campaigns.end(id, sellerId);
    }

    @GetMapping("/rewards/pending")
    public List<PendingReward> pendingRewards(@PathVariable String id,
            @RequestHeader("X-Demo-User") String sellerId,
            @RequestHeader("X-Demo-Role") String role) {
        DemoAuth.require(role, "SELLER");
        return campaigns.pendingRewards(id, sellerId).stream().map(PendingReward::from).toList();
    }

    @PostMapping("/rewards/cancel")
    public CancelResult cancelRewards(@PathVariable String id,
            @RequestHeader("X-Demo-User") String sellerId,
            @RequestHeader("X-Demo-Role") String role,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody CancelRequest request) {
        DemoAuth.require(role, "SELLER");
        var canceled = campaigns.cancelRewards(id, sellerId, request.entryIds(),
                correlationId == null ? UUID.randomUUID().toString() : correlationId);
        return new CancelResult(canceled.stream().map(Entry::id).toList());
    }

    public record PendingReward(String entryId, String userId, long sequence, Instant wonAt) {
        static PendingReward from(Entry entry) {
            return new PendingReward(entry.id(), entry.userId(), entry.sequence(), entry.submittedAt());
        }
    }
    public record CancelRequest(@NotEmpty List<@NotBlank String> entryIds) {}
    public record CancelResult(List<String> canceledEntryIds) {}
}
