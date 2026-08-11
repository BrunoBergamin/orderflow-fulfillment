package br.com.bergamin.fulfillment.application.service;

import br.com.bergamin.fulfillment.application.common.PageQuery;
import br.com.bergamin.fulfillment.application.common.PagedResult;
import br.com.bergamin.fulfillment.application.port.in.FindNotificationUseCase;
import br.com.bergamin.fulfillment.application.port.out.NotificationRepositoryPort;
import br.com.bergamin.fulfillment.domain.model.Notification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta do historico de entregas, inclusive as que morreram, para inspecao. */
@Service
public class NotificationQueryService implements FindNotificationUseCase {

    private final NotificationRepositoryPort notifications;

    public NotificationQueryService(NotificationRepositoryPort notifications) {
        this.notifications = notifications;
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResult<Notification> search(Filter filter, PageQuery pageQuery) {
        return notifications.search(filter, pageQuery);
    }
}
