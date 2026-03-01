CREATE TABLE `files` (
	`filename` text PRIMARY KEY NOT NULL,
	`user_id` text NOT NULL,
	`node_id` text,
	`mime_type` text DEFAULT '' NOT NULL,
	`size` integer DEFAULT 0 NOT NULL,
	`created_at` integer NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
