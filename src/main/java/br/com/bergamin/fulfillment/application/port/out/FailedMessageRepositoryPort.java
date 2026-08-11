package br.com.bergamin.fulfillment.application.port.out;

import br.com.bergamin.fulfillment.application.common.PageQuery;
import br.com.bergamin.fulfillment.application.common.PagedResult;
import br.com.bergamin.fulfillment.domain.model.FailedMessage;
import br.com.bergamin.fulfillment.domain.model.FailedMessageStatus;

import java.util.Optional;
import java.util.UUID;

public interface FailedMessageRepositoryPort {

    FailedMessage save(FailedMessage message);

    Optional<FailedMessage> findById(UUID messageId);

    PagedResult<FailedMessage> search(FailedMessageStatus status, PageQuery pageQuery);

    long countByStatus(FailedMessageStatus status);
}
