import { readFileSync, writeFileSync } from 'node:fs';

/** Add the `sidebar.menu_aria` key (hamburger button label) to every locale. */
const VALUES = {
  zh: '打开导航菜单',
  en: 'Open navigation menu',
  ru: 'Открыть меню навигации',
};

for (const lang of ['zh', 'en', 'ru']) {
  const file = `src/i18n/locales/${lang}.json`;
  let text = readFileSync(file, 'utf8');
  const needle = /(\n(\s*)"nav_label": ".*?",)/;
  if (!needle.test(text)) throw new Error(`${lang}: nav_label anchor not found`);
  text = text.replace(needle, `$1\n$2"menu_aria": "${VALUES[lang]}",`);
  writeFileSync(file, text, 'utf8');
  console.log(`${lang}.json: added sidebar.menu_aria`);
}
