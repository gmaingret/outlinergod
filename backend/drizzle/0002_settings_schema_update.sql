ALTER TABLE `settings` ADD `density` text NOT NULL DEFAULT 'cozy';--> statement-breakpoint
ALTER TABLE `settings` ADD `density_hlc` text NOT NULL DEFAULT '';--> statement-breakpoint
ALTER TABLE `settings` ADD `show_guide_lines` integer NOT NULL DEFAULT 1;--> statement-breakpoint
ALTER TABLE `settings` ADD `show_guide_lines_hlc` text NOT NULL DEFAULT '';--> statement-breakpoint
ALTER TABLE `settings` ADD `show_backlink_badge` integer NOT NULL DEFAULT 1;--> statement-breakpoint
ALTER TABLE `settings` ADD `show_backlink_badge_hlc` text NOT NULL DEFAULT '';--> statement-breakpoint
ALTER TABLE `settings` DROP COLUMN `font_size`;--> statement-breakpoint
ALTER TABLE `settings` DROP COLUMN `font_size_hlc`;