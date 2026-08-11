package br.com.bergamin.fulfillment.application.port.in;

/** Caso de uso: entregar as notificacoes que ja venceram a espera. */
public interface DispatchNotificationsUseCase {

    Report dispatchDue();

    record Report(int attempted, int sent, int retryScheduled, int dead) {

        public static Report empty() {
            return new Report(0, 0, 0, 0);
        }
    }
}
