-- PostgreSQL schema for AI interview features
-- Applied first for resume analysis; knowledge base and interview tables can reuse the same pattern.

create table if not exists ai_resume (
    resume_id bigserial primary key,
    user_id bigint not null,
    original_filename varchar(255) not null,
    storage_key varchar(512) not null,
    storage_url text not null,
    file_size bigint not null default 0,
    content_type varchar(128),
    resume_text text,
    analysis_status int not null default 0,
    analysis_score int,
    analysis_summary text,
    strengths_json text,
    weaknesses_json text,
    recommendations_json text,
    ai_model_name varchar(128),
    create_by bigint,
    create_time timestamp,
    update_by bigint,
    update_time timestamp
);

create index if not exists idx_ai_resume_user_create_time
    on ai_resume (user_id, create_time desc);

create table if not exists ai_resume_analysis_log (
    log_id bigserial primary key,
    resume_id bigint not null,
    user_id bigint not null,
    analysis_text text not null,
    ai_model_name varchar(128),
    create_by bigint,
    create_time timestamp,
    update_by bigint,
    update_time timestamp
);

create index if not exists idx_ai_resume_analysis_log_resume_id
    on ai_resume_analysis_log (resume_id, create_time desc);
