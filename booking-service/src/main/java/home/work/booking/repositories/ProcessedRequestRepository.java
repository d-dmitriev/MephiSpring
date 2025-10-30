package home.work.booking.repositories;

import home.work.booking.entities.ProcessedRequest;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Transactional
public interface ProcessedRequestRepository extends ReactiveCrudRepository<ProcessedRequest, String> {
    Mono<Boolean> existsByRequestId(String requestId);
}