create table if not exists logs
(
    id        varchar(36)                           primary key,
    Level     varchar(255)                          not null,
    Action    varchar(255)                          null,
    Message   text                                  not null,
    Time      bigint                                not null
);

create table logs_context
(
    id      int auto_increment primary key,
    LogId   varchar(36)  not null,
    Context varchar(255) not null,
    Value   varchar(255) not null
);

ALTER TABLE logs_context ADD INDEX (LogId);
ALTER TABLE logs_context ADD INDEX (Context, Value);

create table if not exists uuiditems
(
    UUID        varchar(36)     PRIMARY KEY,
    Namespace   varchar(255)    NOT NULL,
    Keyname     varchar(255)    NOT NULL
);

DROP PROCEDURE IF EXISTS GetLogMessagesByContextAndValue;
CREATE PROCEDURE GetLogMessagesByContextAndValue(IN context_param VARCHAR(255), IN value_param VARCHAR(255))
BEGIN
    SELECT
        l.Message,
        l.action,
        l.time,
        GROUP_CONCAT(CONCAT(lc.context, '::', lc.value) SEPARATOR '|') as context_values
    FROM
        logs_context lc
            INNER JOIN
        logs l ON lc.LogId = l.id
    WHERE
        lc.LogId IN (
            SELECT LogId FROM logs_context WHERE Context = context_param AND Value = value_param
        )
    GROUP BY
        lc.LogId, l.time
    ORDER BY
        l.time DESC;
END;

DROP PROCEDURE IF EXISTS GetLogMessagesByContextAndAction;
CREATE PROCEDURE GetLogMessagesByContextAndAction(IN context_param VARCHAR(255), IN value_param VARCHAR(255), IN action_param VARCHAR(255))
BEGIN
    SELECT
        l.Message,
        l.action,
        l.time,
        GROUP_CONCAT(CONCAT(lc.context, '::', lc.value) SEPARATOR '|') as context_values
    FROM
        logs_context lc
            INNER JOIN
        logs l ON lc.LogId = l.id
    WHERE
        lc.LogId IN (
            SELECT LogId FROM logs_context WHERE Context = context_param AND Value = value_param
        ) AND l.Action LIKE CONCAT(action_param, '%')
    GROUP BY
        lc.LogId, l.time
    ORDER BY
        l.time DESC;
END
