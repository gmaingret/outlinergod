DROP TABLE IF EXISTS `bookmarks`;--> statement-breakpoint
CREATE TABLE `bookmarks` (
	`id` text PRIMARY KEY NOT NULL,
	`user_id` text NOT NULL,
	`title` text NOT NULL,
	`title_hlc` text NOT NULL,
	`target_type` text NOT NULL,
	`target_type_hlc` text NOT NULL,
	`target_document_id` text,
	`target_document_id_hlc` text NOT NULL,
	`target_node_id` text,
	`target_node_id_hlc` text NOT NULL,
	`query` text,
	`query_hlc` text NOT NULL,
	`sort_order` text NOT NULL,
	`sort_order_hlc` text NOT NULL,
	`deleted_at` integer,
	`deleted_hlc` text NOT NULL,
	`device_id` text NOT NULL,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
