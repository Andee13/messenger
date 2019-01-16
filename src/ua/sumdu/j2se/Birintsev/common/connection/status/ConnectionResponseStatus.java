package ua.sumdu.j2se.Birintsev.common.connection.status;

import java.io.Serializable;

public enum ConnectionResponseStatus implements Serializable {
    CREATED,
    ACCEPTED,
    DENIED,
    REGISTRATION_REQUESTED,
    ERROR
}
