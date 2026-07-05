create database thumb_db;
use thumb_db;
drop table user
-- 用户表
create table if not exists user
(
    id           bigint auto_increment primary key,
    userAccount  varchar(32)  not null comment '登录账号',
    userPassword varchar(255) not null comment '加密密码',
    isDelete     tinyint      default 0 not null comment '是否删除：0-否，1-是',
    constraint uk_userAccount unique (userAccount)
);

-- 博客表
create table if not exists blog
(
    id         bigint auto_increment primary key,
    userId     bigint                             not null,
    title      varchar(512)                       null comment '标题',
    coverImg   varchar(1024)                      null comment '封面',
    content    text                               not null comment '内容',
    thumbCount int      default 0                 not null comment '点赞数',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间'
);
create index idx_userId on blog (userId);

-- 点赞记录表
create table if not exists thumb
(
    id         bigint auto_increment primary key,
    userId     bigint                             not null,
    blogId     bigint                             not null,
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间'
);
create unique index idx_userId_blogId on thumb (userId, blogId);