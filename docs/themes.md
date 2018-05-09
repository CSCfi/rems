# Custom Themes

Custom themes can be added by creating a file, for example
my-custom-theme.edn, to the resources/themes folder. The theming
allows custom themes to only partially override the default
attributes. To take the new theme into use add a key/value pair,
`:theme "my-custom-theme"` in the case of your example, to the
appropriate config.edn under the env folder.

To quickly validate that all UI components look right navigate to `/#/guide`. See it in action at <https://rems2demo.csc.fi/#/guide>.