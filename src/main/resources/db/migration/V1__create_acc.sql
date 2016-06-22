-- Database generated with pgModeler (PostgreSQL Database Modeler).
-- pgModeler  version: 0.8.2-beta1
-- PostgreSQL version: 9.5
-- Project Site: pgmodeler.com.br
-- Model Author: ---


-- Database creation must be done outside an multicommand file.
-- These commands were put in this file only for convenience.
-- -- object: new_database | type: DATABASE --
-- -- DROP DATABASE IF EXISTS new_database;
-- CREATE DATABASE new_database
-- ;
-- -- ddl-end --
--

-- object: ricash | type: SCHEMA --
-- DROP SCHEMA IF EXISTS ricash CASCADE;
CREATE SCHEMA ricash;
-- ddl-end --
ALTER SCHEMA ricash
OWNER TO rslima;
-- ddl-end --

-- object: "uuid-ossp" | type: EXTENSION --
-- DROP EXTENSION IF EXISTS "uuid-ossp" CASCADE;
CREATE EXTENSION "uuid-ossp"
WITH SCHEMA ricash;
-- ddl-end --

SET search_path TO pg_catalog, public, ricash;
-- ddl-end --

-- object: ricash.acc | type: TABLE --
-- DROP TABLE IF EXISTS ricash.acc CASCADE;
CREATE TABLE ricash.acc (
  id       UUID         NOT NULL DEFAULT uuid_generate_v1mc(),
  name     VARCHAR(500) NOT NULL,
  group_id UUID         NOT NULL,
  cash     BIT          NOT NULL DEFAULT B'0',
  book_id  UUID         NOT NULL,
  CONSTRAINT acc_pk PRIMARY KEY (id)

);
-- ddl-end --
ALTER TABLE ricash.acc
  OWNER TO rslima;
-- ddl-end --

-- object: pk_acc | type: INDEX --
-- DROP INDEX IF EXISTS ricash.pk_acc CASCADE;
CREATE INDEX pk_acc ON ricash.acc
USING BTREE
(
  id
);
-- ddl-end --

-- object: ricash."accGroup" | type: TABLE --
-- DROP TABLE IF EXISTS ricash."accGroup" CASCADE;
CREATE TABLE ricash."accGroup" (
  id  UUID        NOT NULL DEFAULT uuid_generate_v1mc(),
  key VARCHAR(50) NOT NULL,
  CONSTRAINT "accGroup_pk" PRIMARY KEY (id)

);
-- ddl-end --
ALTER TABLE ricash."accGroup"
  OWNER TO rslima;
-- ddl-end --

-- object: ricash.book | type: TABLE --
-- DROP TABLE IF EXISTS ricash.book CASCADE;
CREATE TABLE ricash.book (
  id       UUID    NOT NULL DEFAULT uuid_generate_v1mc(),
  name     VARCHAR NOT NULL,
  owner_id UUID    NOT NULL,
  CONSTRAINT book_pk PRIMARY KEY (id)

);
-- ddl-end --
ALTER TABLE ricash.book
  OWNER TO rslima;
-- ddl-end --

-- object: ricash.usr | type: TABLE --
-- DROP TABLE IF EXISTS ricash.usr CASCADE;
CREATE TABLE ricash.usr (
  id       UUID         NOT NULL DEFAULT uuid_generate_v1mc(),
  login    VARCHAR(50)  NOT NULL,
  email    VARCHAR(50)  NOT NULL,
  password VARCHAR(100) NOT NULL,
  name     VARCHAR(100),
  CONSTRAINT usr_pk PRIMARY KEY (id),
  CONSTRAINT login_un UNIQUE (login),
  CONSTRAINT email_un UNIQUE (email)

);
-- ddl-end --
ALTER TABLE ricash.usr
  OWNER TO rslima;
-- ddl-end --

-- object: "acc_accGroup_fk" | type: CONSTRAINT --
-- ALTER TABLE ricash.acc DROP CONSTRAINT IF EXISTS "acc_accGroup_fk" CASCADE;
ALTER TABLE ricash.acc
  ADD CONSTRAINT "acc_accGroup_fk" FOREIGN KEY (group_id)
REFERENCES ricash."accGroup" (id) MATCH FULL
ON DELETE RESTRICT ON UPDATE CASCADE;
-- ddl-end --

-- object: acc_book_fk | type: CONSTRAINT --
-- ALTER TABLE ricash.acc DROP CONSTRAINT IF EXISTS acc_book_fk CASCADE;
ALTER TABLE ricash.acc
  ADD CONSTRAINT acc_book_fk FOREIGN KEY (book_id)
REFERENCES ricash.book (id) MATCH FULL
ON DELETE CASCADE ON UPDATE CASCADE;
-- ddl-end --

-- object: usr_book_fk | type: CONSTRAINT --
-- ALTER TABLE ricash.book DROP CONSTRAINT IF EXISTS usr_book_fk CASCADE;
ALTER TABLE ricash.book
  ADD CONSTRAINT usr_book_fk FOREIGN KEY (owner_id)
REFERENCES ricash.usr (id) MATCH FULL
ON DELETE CASCADE ON UPDATE CASCADE;
-- ddl-end --


