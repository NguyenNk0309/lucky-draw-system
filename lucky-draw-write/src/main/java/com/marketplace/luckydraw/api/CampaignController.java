package com.marketplace.luckydraw.api;

import com.marketplace.luckydraw.domain.Campaign;
import com.marketplace.luckydraw.domain.Reward;
import com.marketplace.luckydraw.service.CampaignService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/campaigns")
public class CampaignController {
    private final CampaignService service;

    public CampaignController(CampaignService service) {
        this.service = service;
    }

    @GetMapping
    public List<Campaign> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public Campaign get(@PathVariable String id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Campaign create(
            @RequestHeader("X-Demo-User") String sellerId,
            @RequestHeader("X-Demo-Role") String role,
            @Valid @RequestBody CreateCampaign request) {
        DemoAuth.require(role, "SELLER");
        return service.create(sellerId, request.name(), request.startAt(), request.endAt(),
                request.maxEntriesPerUser(), new Reward(request.rewardType(), request.rewardReference()));
    }

    @PostMapping("/{id}/activate")
    public Campaign activate(@PathVariable String id, @RequestHeader("X-Demo-User") String sellerId,
            @RequestHeader("X-Demo-Role") String role) {
        DemoAuth.require(role, "SELLER");
        return service.activate(id, sellerId);
    }

    @PostMapping("/{id}/cancel")
    public Campaign cancel(@PathVariable String id, @RequestHeader("X-Demo-User") String sellerId,
            @RequestHeader("X-Demo-Role") String role) {
        DemoAuth.require(role, "SELLER");
        return service.cancel(id, sellerId);
    }

    @PostMapping("/{id}/end")
    public Campaign end(@PathVariable String id, @RequestHeader("X-Demo-User") String sellerId,
            @RequestHeader("X-Demo-Role") String role) {
        DemoAuth.require(role, "SELLER");
        return service.end(id, sellerId);
    }

    public record CreateCampaign(
            @NotBlank String name,
            @NotNull Instant startAt,
            @NotNull @Future Instant endAt,
            @Min(1) int maxEntriesPerUser,
            @NotNull Reward.Type rewardType,
            @NotBlank String rewardReference
    ) {}
}

