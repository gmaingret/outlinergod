-- Remove the FOREIGN KEY constraint on nodes.parent_id.
-- parent_id is used as a sentinel: documentId for root nodes, null or a node id otherwise.
-- The FK (parent_id -> nodes.id) incorrectly rejects root nodes whose parent_id is a document id.
-- SQLite requires recreating the table to drop a FK constraint.
-- Note: PRAGMA foreign_keys is a no-op inside Drizzle's transaction wrapper; omitted here.

CREATE TABLE `nodes_new` (
	`id` text PRIMARY KEY NOT NULL,
	`document_id` text NOT NULL,
	`user_id` text NOT NULL,
	`content` text DEFAULT '' NOT NULL,
	`content_hlc` text NOT NULL,
	`note` text DEFAULT '' NOT NULL,
	`note_hlc` text NOT NULL,
	`parent_id` text,
	`parent_id_hlc` text NOT NULL,
	`sort_order` text NOT NULL,
	`sort_order_hlc` text NOT NULL,
	`completed` integer DEFAULT 0 NOT NULL,
	`completed_hlc` text NOT NULL,
	`color` integer DEFAULT 0 NOT NULL,
	`color_hlc` text NOT NULL,
	`collapsed` integer DEFAULT 0 NOT NULL,
	`collapsed_hlc` text NOT NULL,
	`deleted_at` integer,
	`deleted_hlc` text NOT NULL,
	`device_id` text NOT NULL,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	FOREIGN KEY (`document_id`) REFERENCES `documents`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
INSERT INTO `nodes_new` SELECT * FROM `nodes`;
--> statement-breakpoint
DROP TABLE `nodes`;
--> statement-breakpoint
ALTER TABLE `nodes_new` RENAME TO `nodes`;
