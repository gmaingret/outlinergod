CREATE TABLE `users` (
	`id` text PRIMARY KEY NOT NULL,
	`google_sub` text NOT NULL,
	`email` text NOT NULL,
	`name` text NOT NULL,
	`picture` text DEFAULT '' NOT NULL,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL
);
--> statement-breakpoint
CREATE UNIQUE INDEX `users_google_sub_unique` ON `users` (`google_sub`);--> statement-breakpoint
CREATE TABLE `refresh_tokens` (
	`token` text PRIMARY KEY NOT NULL,
	`user_id` text NOT NULL,
	`device_id` text NOT NULL,
	`expires_at` integer NOT NULL,
	`created_at` integer NOT NULL,
	`revoked` integer DEFAULT 0 NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE TABLE `documents` (
	`id` text PRIMARY KEY NOT NULL,
	`user_id` text NOT NULL,
	`title` text NOT NULL,
	`title_hlc` text NOT NULL,
	`type` text NOT NULL,
	`parent_id` text,
	`parent_id_hlc` text NOT NULL,
	`sort_order` text NOT NULL,
	`sort_order_hlc` text NOT NULL,
	`collapsed` integer DEFAULT 0 NOT NULL,
	`collapsed_hlc` text NOT NULL,
	`deleted_at` integer,
	`deleted_hlc` text NOT NULL,
	`device_id` text NOT NULL,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`parent_id`) REFERENCES `documents`(`id`) ON UPDATE no action ON DELETE no action
);
--> statement-breakpoint
CREATE TABLE `nodes` (
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
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`parent_id`) REFERENCES `nodes`(`id`) ON UPDATE no action ON DELETE no action
);
--> statement-breakpoint
CREATE TABLE `bookmarks` (
	`id` text PRIMARY KEY NOT NULL,
	`user_id` text NOT NULL,
	`node_id` text NOT NULL,
	`document_id` text NOT NULL,
	`sort_order` text NOT NULL,
	`sort_order_hlc` text NOT NULL,
	`deleted_at` integer,
	`deleted_hlc` text NOT NULL,
	`device_id` text NOT NULL,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`node_id`) REFERENCES `nodes`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`document_id`) REFERENCES `documents`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE TABLE `settings` (
	`id` text PRIMARY KEY NOT NULL,
	`user_id` text NOT NULL,
	`theme` text DEFAULT 'system' NOT NULL,
	`theme_hlc` text NOT NULL,
	`font_size` integer DEFAULT 14 NOT NULL,
	`font_size_hlc` text NOT NULL,
	`device_id` text NOT NULL,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
