# Webアプリ版セットアップ手順

このWeb版は、初心者でも公開しやすいように次の構成にしています。

- 画面: GitHub Pages
- 認証、データベース、管理者のユーザ追加: Supabase
- ビルド: 不要
- 公開フォルダ: `docs`

GitHub PagesはHTML、CSS、JavaScriptを置くだけの静的ホスティングです。パスワードや管理者処理は安全に置けないため、Supabaseに任せます。

## 1. Supabaseを作る

1. Supabaseにログインします。
2. New Projectを作ります。
3. Freeプランで開始します。
4. Project URLとanon public keyを控えます。
   - Dashboard > Project Settings > API
   - `Project URL`
   - `anon public`

## 2. データベースを作る

1. Supabase Dashboardで SQL Editor を開きます。
2. `supabase/schema.sql` の中身をすべて貼り付けます。
3. Runを押します。

これで、テーブル、権限、初期食材マスタが作成されます。

## 3. 最初の管理者を作る

最初の管理者だけは、Supabase Dashboardで手動作成します。以後のユーザ追加はWebアプリの管理画面からできます。

1. `docs/id-helper.html` をブラウザで開きます。
2. 管理者IDを入力します。例: `admin`
3. 表示されたメールアドレスをコピーします。
4. Supabase Dashboard > Authentication > Users を開きます。
5. Add userを押します。
6. Emailに、コピーしたメールアドレスを入れます。
7. Passwordに、管理者パスワードを入れます。
8. Auto Confirm Userがあれば有効にします。
9. 作成後、User UIDをコピーします。

次に SQL Editor で以下を実行します。`PASTE_AUTH_USER_UID_HERE` と `admin` は自分の内容に置き換えてください。

```sql
with h as (
  insert into public.households (name)
  values ('管理者')
  returning id
)
insert into public.profiles (
  id,
  login_id,
  display_name,
  role,
  household_id,
  is_active
)
select
  'PASTE_AUTH_USER_UID_HERE',
  'admin',
  '管理者',
  'admin',
  h.id,
  true
from h;
```

## 4. 管理者のユーザ追加機能を有効にする

管理者がWebアプリからユーザを追加するには、Supabase Edge Functionをデプロイします。

### Node.jsを入れていない場合

Node.js LTS版をインストールしてください。

### コマンド

PowerShellやターミナルで、このプロジェクトのフォルダを開き、以下を実行します。

```powershell
npx supabase login
npx supabase link --project-ref YOUR_PROJECT_REF
npx supabase functions deploy admin-create-user
```

`YOUR_PROJECT_REF` は Supabase のURLに含まれるプロジェクトIDです。

例:

```text
https://abcdefghijklmnop.supabase.co
```

この場合の `YOUR_PROJECT_REF` は `abcdefghijklmnop` です。

## 5. WebアプリにSupabase情報を設定する

`docs/config.js` を開き、以下を置き換えます。

```js
window.APP_CONFIG = {
  SUPABASE_URL: "https://YOUR_PROJECT_REF.supabase.co",
  SUPABASE_ANON_KEY: "YOUR_SUPABASE_ANON_KEY",
  APP_NAME: "離乳食アレルギーチェック",
};
```

`SUPABASE_ANON_KEY` はブラウザに置いてよい公開キーです。ただし、service role keyは絶対に入れないでください。

## 6. GitHub Pagesで公開する

1. GitHubで新しいリポジトリを作ります。
2. このプロジェクトのファイルをアップロードします。
3. リポジトリの Settings を開きます。
4. Pages を開きます。
5. Build and deployment の Source を Deploy from a branch にします。
6. Branchを `main`、Folderを `/docs` にします。
7. Saveを押します。

数分後、GitHub PagesのURLが表示されます。

## 7. 使い始める

1. GitHub PagesのURLを開きます。
2. 管理者IDとパスワードでログインします。
3. 下部の「管理」を開きます。
4. ユーザID、初期パスワード、表示名、家庭名を入力してユーザ追加します。
5. 追加したユーザにIDとパスワードを伝えます。
6. ユーザはログイン後、子供を登録して記録を開始できます。

## 仕様メモ

- IDは最大12文字です。
- 使えるID文字は、英数字と `. _ -` です。
- パスワードは8文字以上です。
- 管理者は全ユーザ、全家庭、全記録を見られます。
- 通常ユーザは自分の家庭の子供、記録、家庭用食材だけを編集できます。
- 食材マスタは、共通食材と家庭用食材に分かれます。
- 共通食材は管理者が編集します。
- 家庭用食材は、その家庭だけで表示、編集できます。
- オフライン利用は対応していません。

## 参考リンク

- GitHub Pages: https://docs.github.com/pages
- Supabase Auth: https://supabase.com/docs/guides/auth
- Supabase Edge Functions: https://supabase.com/docs/guides/functions
- Supabase Row Level Security: https://supabase.com/docs/guides/database/postgres/row-level-security
