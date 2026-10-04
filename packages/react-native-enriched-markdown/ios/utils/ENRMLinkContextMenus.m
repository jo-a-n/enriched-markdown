#import "ENRMLinkContextMenus.h"
#import "LinkTapUtils.h"

@implementation ENRMLinkContextMenuItem
@end

@implementation ENRMLinkContextMenuEntry
@end

@implementation ENRMLinkContextMenus {
  NSArray<NSRegularExpression *> *_regexes;
  // A long press asks about the same URL several times (gesture gating, then the menu).
  NSMutableDictionary<NSString *, id> *_entriesByURL;
}

- (instancetype)init
{
  if (self = [super init]) {
    _entries = @[];
    _regexes = @[];
    _entriesByURL = [NSMutableDictionary new];
  }
  return self;
}

- (void)setEntries:(NSArray<ENRMLinkContextMenuEntry *> *)entries
{
  NSMutableArray<ENRMLinkContextMenuEntry *> *valid = [NSMutableArray arrayWithCapacity:entries.count];
  NSMutableArray<NSRegularExpression *> *regexes = [NSMutableArray arrayWithCapacity:entries.count];
  for (ENRMLinkContextMenuEntry *entry in entries) {
    NSRegularExpression *regex = [NSRegularExpression regularExpressionWithPattern:entry.pattern options:0 error:nil];
    if (!regex || entry.items.count == 0)
      continue;
    [valid addObject:entry];
    [regexes addObject:regex];
  }
  _entries = [valid copy];
  _regexes = [regexes copy];
  [_entriesByURL removeAllObjects];
}

- (nullable ENRMLinkContextMenuEntry *)entryForURL:(NSString *)url
{
  if (url.length == 0 || _entries.count == 0)
    return nil;
  id cached = _entriesByURL[url];
  if (cached)
    return cached == NSNull.null ? nil : cached;

  ENRMLinkContextMenuEntry *match = nil;
  NSRange whole = NSMakeRange(0, url.length);
  for (NSUInteger index = 0; index < _regexes.count; index++) {
    if ([_regexes[index] rangeOfFirstMatchInString:url options:0 range:whole].location != NSNotFound) {
      match = _entries[index];
      break;
    }
  }
  _entriesByURL[url] = match ?: NSNull.null;
  return match;
}

- (BOOL)hasMenuForURL:(NSString *)url
{
#if !TARGET_OS_OSX
  if (@available(iOS 17.0, *))
    return [self entryForURL:url] != nil;
#endif
  return NO;
}

#if !TARGET_OS_OSX
- (UIMenu *)menuForURL:(NSString *)url title:(NSString *)title
{
  if (![self hasMenuForURL:url])
    return nil;
  ENRMLinkContextMenuEntry *entry = [self entryForURL:url];
  NSString *pattern = entry.pattern;
  NSMutableArray<UIAction *> *actions = [NSMutableArray arrayWithCapacity:entry.items.count];
  __weak ENRMLinkContextMenus *weakSelf = self;
  for (ENRMLinkContextMenuItem *item in entry.items) {
    NSString *text = item.text;
    UIAction *action = [UIAction actionWithTitle:text
                                           image:item.icon.length > 0 ? [UIImage systemImageNamed:item.icon] : nil
                                      identifier:nil
                                         handler:^(__kindof UIAction *pressed) {
                                           ENRMLinkContextMenuPressHandler onPress = weakSelf.onPress;
                                           if (onPress)
                                             onPress(url, pattern, text);
                                         }];
    if (item.disabled)
      action.attributes |= UIMenuElementAttributesDisabled;
    if (item.destructive)
      action.attributes |= UIMenuElementAttributesDestructive;
    [actions addObject:action];
  }
  return [UIMenu menuWithTitle:title ?: @"" children:actions];
}
#endif
@end

#if !TARGET_OS_OSX
UITextItemMenuConfiguration *ENRMLinkMenuConfigurationForTextItem(UITextView *textView, UITextItem *textItem,
                                                                  UIMenu *defaultMenu, ENRMLinkContextMenus *menus,
                                                                  BOOL linkPreviewEnabled,
                                                                  void (^onLinkLongPress)(NSString *url))
{
  NSString *url = linkURLAtRange(textView, textItem.range);
  UIMenu *menu = [menus menuForURL:url title:linkTitleAtIndex(textView.textStorage, textItem.range.location)];
  if (menu)
    return [UITextItemMenuConfiguration configurationWithPreview:nil menu:menu];
  if (url && !linkPreviewEnabled) {
    if (onLinkLongPress)
      onLinkLongPress(url);
    return nil;
  }
  return [UITextItemMenuConfiguration configurationWithMenu:defaultMenu];
}
#endif
