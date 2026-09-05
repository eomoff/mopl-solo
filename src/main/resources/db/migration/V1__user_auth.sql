-- 사용자와 인증
-- 근거: [도메인 모델 확정] PK는 UUID, [인증·인가와 토큰 무효화 설계] Q1·Q2·Q5·Q6

create table users (
    id                            uuid         primary key,
    created_at                    timestamptz  not null,
    updated_at                    timestamptz  not null,
    email                         varchar(320) not null,
    name                          varchar(50)  not null,
    -- 소셜 가입만 한 계정은 비밀번호 없음
    password                      varchar(60),
    profile_image_url             varchar(2048),
    role                          varchar(20)  not null,
    locked                        boolean      not null default false,
    -- 임시 비밀번호는 사용자 상태의 일부 (Q6)
    temporary_password            varchar(60),
    temporary_password_expires_at timestamptz,
    -- 어드민 초기화의 경합을 이 제약에 맡김 ([어드민 계정 초기화 규칙] Q3)
    constraint uk_users_email unique (email)
);

-- (provider, provider_id) -> user_id 연결 (Q5)
create table social_accounts (
    id          uuid        primary key,
    created_at  timestamptz not null,
    user_id     uuid        not null,
    provider    varchar(20) not null,
    provider_id varchar(255) not null,
    constraint fk_social_accounts_user foreign key (user_id) references users (id),
    constraint uk_social_accounts_provider unique (provider, provider_id)
);

-- 저장·회전하는 리프레시 토큰 (Q1)
create table refresh_tokens (
    id         uuid         primary key,
    created_at timestamptz  not null,
    updated_at timestamptz  not null,
    user_id    uuid         not null,
    token      varchar(255) not null,
    expires_at timestamptz  not null,
    constraint fk_refresh_tokens_user foreign key (user_id) references users (id),
    -- 사용자당 유효 토큰 1개 = 동시 로그인 차단 (Q2)
    constraint uk_refresh_tokens_user unique (user_id),
    constraint uk_refresh_tokens_token unique (token)
);
