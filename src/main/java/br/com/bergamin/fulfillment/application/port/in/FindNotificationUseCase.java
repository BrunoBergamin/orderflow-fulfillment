package br.com.bergamin.fulfillment.application.port.in;

import br.com.bergamin.fulfillment.application.common.PageQuery;
import br.com.bergamin.fulfillment.application.common.PagedResult;
import br.com.bergamin.fulfillment.domain.model.Notification;
import br.com.bergamin.fulfillment.domain.model.NotificationStatus;

import java.util.UUID;

/** Caso de uso: acompanhar o histórico de entregas. */
public interface FindNotificationUseCase {

    PagedResult<Notification> search(Filter filter, PageQuery pageQuery);

    record Filter(UUID orderId, NotificationStatus status) {

        public static Filter none() {
            return new Filter(null, null);
        }
    }
}
