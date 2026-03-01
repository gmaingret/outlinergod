CREATE TABLE `nodes` (
	`id` text PRIMARY KEY NOT NULL,
	`content` text DEFAULT '' NOT NULL,
	`content_hlc` text DEFAULT '' NOT NULL,
	`note` text DEFAULT '' NOT NULL,
	`note_hlc` text DEFAULT '' NOT NULL,
	`parent_id` text,
	`parent_id_hlc` text DEFAULT '' NOT NULL,
	`sort_order` text DEFAULT '' NOT NULL,
	`sort_order_hlc` text DEFAULT '' NOT NULL,
	`completed` integer DEFAULT 0 NOT NULL,
	`completed_hlc` text DEFAULT '' NOT NULL,
	`color` integer DEFAULT 0 NOT NULL,
	`color_hlc` text DEFAULT '' NOT NULL,
	`collapsed` integer DEFAULT 0 NOT NULL,
	`collapsed_hlc` text DEFAULT '' NOT NULL,
	`deleted_at` integer,
	`deleted_hlc` text DEFAULT '' NOT NULL,
	`device_id` text DEFAULT '' NOT NULL,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL
);
--> statement-breakpoint
CREATE INDEX `idx_nodes_active` ON `nodes` (`parent_id`) WHERE deleted_at IS NULL;
--> statement-breakpoint
CREATE INDEX `idx_nodes_modified` ON `nodes` (`deleted_hlc`);
--> statement-breakpoint
CREATE INDEX `idx_nodes_tombstones` ON `nodes` (`deleted_at`) WHERE deleted_at IS NOT NULL;
