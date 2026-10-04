#pragma once
#import "ENRMUIKit.h"

NS_ASSUME_NONNULL_BEGIN

/// One item of a link's long-press menu.
@interface ENRMLinkContextMenuItem : NSObject
@property (nonatomic, copy) NSString *text;
/// SF Symbol name; empty for no icon.
@property (nonatomic, copy) NSString *icon;
@property (nonatomic, assign) BOOL disabled;
@property (nonatomic, assign) BOOL destructive;
@end

/// The menu items for links whose URL matches `pattern`.
@interface ENRMLinkContextMenuEntry : NSObject
@property (nonatomic, copy) NSString *pattern;
@property (nonatomic, copy) NSArray<ENRMLinkContextMenuItem *> *items;
@end

typedef void (^ENRMLinkContextMenuPressHandler)(NSString *url, NSString *pattern, NSString *itemText);

/**
 * The `linkContextMenuItems` prop: long-press menus for links, by URL pattern.
 * Entries are tried in order, like link variants, and the first whose pattern
 * matches a URL supplies its menu. Menus need iOS 17; below that no URL has one.
 */
@interface ENRMLinkContextMenus : NSObject
@property (nonatomic, copy) NSArray<ENRMLinkContextMenuEntry *> *entries;
@property (nonatomic, copy, nullable) ENRMLinkContextMenuPressHandler onPress;
- (BOOL)hasMenuForURL:(nullable NSString *)url;
#if !TARGET_OS_OSX
/// `title` is what the user sees the link as: its text, or its pill label.
- (nullable UIMenu *)menuForURL:(nullable NSString *)url title:(nullable NSString *)title;
#endif
@end

#if !TARGET_OS_OSX
#ifdef __cplusplus
extern "C" {
#endif

/**
 * What a long press on a text item shows: the link's configured menu, or what links did before
 * menus existed (the system menu with a preview, or `onLinkLongPress` when previews are off).
 * Every text view delegate answers `textView:menuConfigurationForTextItem:defaultMenu:` with this.
 */
UITextItemMenuConfiguration *_Nullable ENRMLinkMenuConfigurationForTextItem(
    UITextView *textView, UITextItem *textItem, UIMenu *defaultMenu, ENRMLinkContextMenus *_Nullable menus,
    BOOL linkPreviewEnabled, void (^_Nullable onLinkLongPress)(NSString *url)) API_AVAILABLE(ios(17.0));

#ifdef __cplusplus
}
#endif
#endif

NS_ASSUME_NONNULL_END
