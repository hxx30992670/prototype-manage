package com.company.prototype.worker.job;

import com.company.prototype.persistence.publish.PublishJobEntity;
import com.company.prototype.persistence.publish.PublishJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "worker.polling.enabled", havingValue = "true", matchIfMissing = true)
public class PublishJobPoller {

    private static final Logger log = LoggerFactory.getLogger(PublishJobPoller.class);

    private final PublishJobRepository publishJobRepository;
    private final PublishJobRunner jobRunner;

    public PublishJobPoller(PublishJobRepository publishJobRepository, PublishJobRunner jobRunner) {
        this.publishJobRepository = publishJobRepository;
        this.jobRunner = jobRunner;
    }

    @Scheduled(fixedDelayString = "${worker.polling.interval-ms:2000}")
    public void pollPendingJobs() {
        Instant staleBefore = Instant.now().minus(Duration.ofMinutes(10));
        List<PublishJobEntity> pendingJobs = publishJobRepository.findClaimable(staleBefore, PageRequest.of(0, 10));
        for (PublishJobEntity job : pendingJobs) {
            try {
                jobRunner.run(job.getId());
            } catch (Exception e) {
                log.error("Error running job {}: {}", job.getId(), e.getMessage(), e);
            }
        }
    }
}
