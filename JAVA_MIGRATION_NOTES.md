# JAVA_MIGRATION_NOTES — SampleMvcWebApp → Java 21 / Spring Boot 3.5

Contract for rationalising the ASP.NET MVC 5 / .NET Framework 4.5.1 / EF6 / GenericServices / Autofac
application onto the org Java 21 standard. The .NET solution in this repo stays **untouched**; the Java
implementation lives entirely under [`java/`](java/).

Every layer (child session) must honour this document. Where this document and the legacy code disagree,
the **legacy behaviour wins** — the legacy app is the spec and the baseline screenshots in
[`java/docs/baseline/`](java/docs/baseline/) are the acceptance target.

## 1. Stack and build

| Concern | Legacy | Java |
|---|---|---|
| Runtime | .NET Framework 4.5.1, IIS Express | Java 21 (Temurin 21.0.9 verified), embedded Tomcat |
| Web | ASP.NET MVC 5 + Razor | Spring MVC + Thymeleaf (Spring Boot 3.5.6) |
| Data | EF6 code-first, SQL Server LocalDB | Spring Data JPA / Hibernate 6; H2 file DB (MSSQLServer mode) by default, `sqlserver` profile for SQL Server |
| Services | GenericServices (DTO ↔ entity) | Hand-written `@Service` classes implementing the interfaces in `servicelayer.services` |
| DI | Autofac modules | Spring component scanning |
| Validation | DataAnnotations + `IValidatableObject` + `DbContext.ValidateEntity` | Jakarta Bean Validation + service-level checks returning `SuccessOrErrors` |
| Tests | NUnit 2.6 (54 tests) | JUnit 5 + Spring Boot test slices on H2, JaCoCo coverage |
| Build | MSBuild | Maven (`java/mvnw`), `mvn verify` must pass on JDK 21 |

Build: `cd java && ./mvnw -B verify` (Windows: `mvnw.cmd -B verify`). Run: `./mvnw spring-boot:run`
→ http://localhost:8080/. If Maven Central returns HTTP 429, use the Google mirror
(`MVNW_REPOURL=https://maven-central.storage-download.googleapis.com/maven2` plus a `~/.m2/settings.xml`
`<mirror>` of `central` to the same URL).

## 2. Package layout (mirrors the .NET projects)

```
java/src/main/java/com/samplemvcwebapp/
  SampleMvcWebAppApplication.java      ← SampleWebApp/Global.asax (@EnableAsync for the *Async controllers)
  datalayer/entities/                  ← DataLayer/DataClasses/Concrete  (Blog, Post, Tag, TrackUpdate)       [A]
  datalayer/repositories/              ← DataLayer/DataClasses/SampleWebAppDb DbSets                        [A]
  datalayer/startup/                   ← DataLayer/Startup (DataLayerInitialiser, TestDataSelection, XML load) [A]
  bizlayer/                            ← BizLayer (no live UI consumer in legacy; keep any port here)        [A]
  servicelayer/                        ← ServiceLayer + GenericServices (SuccessOrErrors, ValidationError)  [A]
  servicelayer/dto/                    ← *Dto classes                                                        [A]
  servicelayer/ui/                     ← ServiceLayer/UiClasses (DropDownListType, MultiSelectListType)       [A]
  servicelayer/services/               ← service interfaces (contract) + implementations                     [A]
  web/                                 ← SampleWebApp/Controllers, Infrastructure, Models                    [B]
java/src/main/resources/
  seed/BlogsContentSimple.xml, seed/BlogsContextMedium.xml   ← verbatim copies of DataLayer/Startup/Internal/*.xml
  templates/<controller>/<action>.html ← SampleWebApp/Views/<Controller>/<Action>.cshtml                    [B]
  templates/layout.html, fragments/    ← Views/Shared/_Layout.cshtml, PostValidation, TagValidation, EditorTemplates [B]
  static/                              ← SampleWebApp/Content (bootstrap 3, Site.css), Scripts (jquery 1.10.2, bootstrap.js, jquery.validate*) [B]
java/src/test/java/com/samplemvcwebapp/   ← Tests/UnitTests (JUnit 5)                                      [C]
```

The skeleton commit already contains the **contract types** (entities, repositories, DTOs, UI classes,
`SuccessOrErrors`, service interfaces, `DataLayerInitialiser`). Children may add to them (annotations,
helper methods, implementations) but must not rename or remove contract members without updating this
document in the same PR.

## 3. Entity model (DataLayer)

Table and column names follow the EF6 code-first schema so the `sqlserver` profile can point at a database
created by the legacy app.

| Entity | Table | Field (Java) | Column | Type | Constraints / legacy annotation |
|---|---|---|---|---|---|
| Blog | Blogs | blogId | BlogId | int identity | PK |
| | | name | Name | nvarchar(64) | `[Required]`, `[MinLength(2)]`, `[MaxLength(64)]` |
| | | emailAddress | EmailAddress | nvarchar(256) | `[Required]`, `[MaxLength(256)]`, `[EmailAddress]` |
| | | posts | — | 1..* Post | cascade delete (deleting a blog deletes its posts) |
| Post | Posts | postId | PostId | int identity | PK |
| | | title | Title | nvarchar(128) | `[Required]`, `[MinLength(2)]`, `[MaxLength(128)]` |
| | | content | Content | nvarchar(max) | `[Required]` |
| | | blogger | BlogId | FK → Blogs | required |
| | | tags | TagPosts(Post_PostId, Tag_TagId) | *..* Tag | ordered by TagId |
| | | lastUpdated | LastUpdated | datetime | `TrackUpdate`: set to UTC now on create and every update |
| Tag | Tags | tagId | TagId | int identity | PK |
| | | slug | Slug | nvarchar(64) | `[Required]`, `[MaxLength(64)]`, `[RegularExpression(@"\w*")]`, **unique** (checked in code, see §6) |
| | | name | Name | nvarchar(128) | `[Required]`, `[MaxLength(128)]` |
| | | posts | — | *..* Post | inverse side |

## 4. Repository and service interfaces

Repositories: `BlogRepository`, `PostRepository`, `TagRepository` — each `JpaRepository<E, Integer>`.
Child A may add derived queries / fetch joins (e.g. `existsBySlugAndTagIdNot`, `findAllWithBloggerAndTags`).

Services (all in `servicelayer.services`; implementations are Spring beans owned by child A):

| Interface | Methods | Used by |
|---|---|---|
| `PostService` | `listPosts(Integer blogId)`, `getDetail(id)`, `getCreateSetup()`, `getUpdateSetup(id)`, `resetDto(dto)`, `create(dto)`, `update(dto)`, `delete(id)`, `getNumPostsMessage()` | `PostsController` |
| `PostAsyncService` | `*Async` equivalents returning `CompletableFuture` (list has no blog filter, like legacy) | `PostsAsyncController` |
| `TagService` | `listTags()`, `getDetail(id)`, `create(dto)`, `update(dto)`, `delete(id)` | `TagsController` |
| `TagAsyncService` | `*Async` equivalents | `TagsAsyncController` |
| `BlogService` | `listBlogs()`, `getDetail(id)`, `create(dto)`, `update(dto)`, `delete(id)` | `BlogsController` |
| `DataLayerInitialiser` | `resetBlogs(TestDataSelection)` | `PostsController.reset`, `PostsAsyncController.reset`, tests |

`SuccessOrErrors` mirrors GenericServices `ISuccessOrErrors`: `isValid()`, `getSuccessMessage()`,
`getErrors()` (list of `ValidationError(propertyName, message)`; `propertyName == null` ⇒ model-level error).
Services perform **all** validation (Bean Validation on the DTO + business rules + slug uniqueness) and
return errors rather than throwing, so the web layer can copy them into `BindingResult`
(legacy `CopyErrorsToModelState`) and the async and sync paths behave identically.

DTOs and their **form field names** (the binding contract between A and B — the HTML `name` attributes
must match the legacy form posts):

| DTO | Legacy | Form fields |
|---|---|---|
| `SimplePostDto` (record) | `SimplePostDto` | read-only list row: postId, blogId, bloggerName, title, tagNames (`", "`-joined, TagId order), lastUpdated |
| `DetailPostDto` | `DetailPostDto` / `DetailPostDtoAsync` | `PostId`, `Title`, `Content`, `Bloggers.SelectedValue`, `UserChosenTags.FinalSelection` (multi) |
| `TagListDto` (record) | `TagListDto` | read-only: tagId, name, slug, postsCount |
| `TagDto` | `Tag` entity | `TagId`, `Name`, `Slug` |
| `BlogListDto` (record) | `BlogListDto` | read-only: blogId, name, emailAddress, postsCount |
| `BlogDto` | `Blog` entity | `BlogId`, `Name`, `EmailAddress` |

The HTML `name=` attributes may be Spring's camelCase paths (`title`, `bloggers.selectedValue`,
`userChosenTags.finalSelection`) — parity is judged on rendered UI and behaviour, not raw POST bodies.
Labels must match the legacy labels (`Title`, `Content`, `Blogger`, `Tags`, `Name`, `Slug`, `EmailAddress`, …)
because the parity walker drives forms by label/`name`.

## 5. Routes and view names (web layer, child B)

Conventional route `{controller}/{action}/{id}`. Java serves the exact legacy casing (`/Posts/Details/19`).
Page `<title>` is `"{ViewBag.Title} - SampleMvcWebApp"` (views with no ViewBag.Title render
`" - SampleMvcWebApp"`). Flash messages (legacy `TempData["message"]`) render in the same place as the
legacy views. Deletes are plain GET links (no confirmation page) that redirect to Index.

| Method & path | Legacy action | Thymeleaf view | `<title>` prefix / heading | Behaviour |
|---|---|---|---|---|
| GET `/`, `/Home`, `/Home/Index` | HomeController.Index | `home/index` | Index / "Welcome to the Sample MVC Web Application - basic version" | static |
| GET `/Home/About` | About | `home/about` | About / "About this site" | static |
| GET `/Home/Contact` | Contact | `home/contact` | Contact / "Contact" | static |
| GET `/Home/CodeView` | CodeView | `home/codeView` | CodeView / "Introduction to GenericServices" | static |
| GET `/Home/Internals` | Internals | `home/internals` | (none) / h4 "InternalsInfo" | JVM thread info in place of .NET ThreadPool numbers |
| GET `/Posts`, `/Posts/Index`, `/Posts/Index/{id}` | PostsController.Index | `posts/index` | Posts / "Posts" | id≠0 ⇒ filter by BlogId, flash "Filtered list" |
| GET `/Posts/Details/{id}` | Details | `posts/details` | Details / "Details" | |
| GET `/Posts/Create` | Create | `posts/create` | Create / "Create" | blogger drop-down + tag multi-select |
| POST `/Posts/Create` | Create | `posts/create` or redirect `/Posts` | | "Successfully created Post." |
| GET `/Posts/Edit/{id}` | Edit | `posts/edit` | Edit / "Edit" | |
| POST `/Posts/Edit/{id}` | Edit | `posts/edit` or redirect `/Posts` | | "Successfully updated Post." |
| GET `/Posts/Delete/{id}` | Delete | redirect `/Posts` | | "Successfully deleted Post." |
| GET `/Posts/NumPosts` | NumPosts | `posts/numPosts` | (none) / h4 "Test" | "The total number of Posts is {n}" |
| GET `/Posts/Delay` | Delay | `posts/delay` | Posts Delay | sleep 500 ms, "I delayed by 500 ms." |
| GET `/Posts/CodeView` | CodeView | `posts/codeView` | CodeView / "Posts: An explanation of Post code" | static |
| GET `/Posts/Reset` | Reset | redirect `/Posts` | | `resetBlogs(MEDIUM)`, flash "Successfully reset the blogs data" |
| `/PostsAsync/{Index,Details,Create,Edit,Delete,NumPosts,Delay,CodeView,Reset}` | PostsAsyncController | `postsAsync/*` | PostsAsync / "Posts (async)"; Details/Create/Edit h2 suffixed " (Async)"; Delay title "PostsAsync Delay"; CodeView h2 "PostsAsync: A explanation of the PostAsync code" | same as Posts (Index has no filter); handlers return `CompletableFuture<String>` |
| GET `/Tags`, `/Tags/Index` | TagsController.Index | `tags/index` | Tags / "Tags" | |
| GET `/Tags/Details/{id}` | Details | `tags/details` | Details / "Details" | |
| GET/POST `/Tags/Create` | Create | `tags/create` | Create / "Create" | "Successfully created Tag." |
| GET/POST `/Tags/Edit/{id}` | Edit | `tags/edit` | Edit / "Edit" | "Successfully updated Tag." |
| GET `/Tags/Delete/{id}` | Delete | redirect `/Tags` | | "Successfully deleted Tag." |
| GET `/Tags/CodeView` | CodeView | `tags/codeView` | CodeView / "Tags: A explanation of Tags code" | static |
| `/TagsAsync/{Index,Details,Create,Edit,Delete,CodeView}` | TagsAsyncController | `tagsAsync/*` | TagsAsync / "Tags (Async)"; Details/Create/Edit h2 suffixed " (Async)" | same as Tags |
| GET `/Blogs`, `/Blogs/Index` | BlogsController.Index | `blogs/index` | (none) / "Blogs" | no Details page |
| GET/POST `/Blogs/Create` | Create | `blogs/create` | (none) / h4 "Blog" | "Successfully created Blog." |
| GET/POST `/Blogs/Edit/{id}` | Edit | `blogs/edit` | (none) / h4 "Blog" | "Successfully updated Blog." |
| GET `/Blogs/Delete/{id}` | Delete | redirect `/Blogs` | | "Successfully deleted Blog." |
| GET `/Error`, unknown id | Shared/Error | `error` | Error | legacy shows the shared Error view |

Layout (`Views/Shared/_Layout.cshtml`): Bootstrap 3 inverse fixed navbar with **Home**, **Sync database ▾**
(Posts → `/Posts/Index/0`, Tags, divider, Blogs), **Async database ▾** (Posts → `/PostsAsync/Index/0`,
Tags), **About**, **Contact**; footer "An open source project under the MIT licence, created by Jon Smith."
with right-aligned "Hosted on {app.host-type}" (legacy shows `Hosted on LocalHost`). Copy the legacy static
assets (bootstrap.css 3.x, Site.css, jquery-1.10.2, bootstrap.js, jquery.validate + unobtrusive) so pages
look the same. Body copy (intro paragraphs, CodeView text, Home text) is copied verbatim from the
`.cshtml` files. Dates render like the legacy en-US default (`M/d/yyyy h:mm:ss a`, local time).
Blog EmailAddress input is `type="email"` (legacy `[EmailAddress]` ⇒ HTML5 email input).

## 6. Validation messages (must match exactly)

Authoritative sources: `DataLayer/DataClasses/Concrete/*.cs`, `DataLayer/DataClasses/SampleWebAppDb.cs`,
`ServiceLayer/PostServices/DetailPostDto.cs`. Field-level errors render next to the field; model-level
errors (property `null`) render in the validation summary, as in the legacy views.

| Where | Rule | Property | Message |
|---|---|---|---|
| Post | Title required | Title | `The Title field is required.` |
| Post | Title min 2 / max 128 | Title | `The field Title must be a string or array type with a minimum length of '2'.` / `...maximum length of '128'.` |
| Post | Content required | Content | `The Content field is required.` |
| Post | Title contains `!` | Title | `Sorry, but you can't get too excited and include a ! in the title.` |
| Post | Title ends with `?` | Title | `Sorry, but you can't ask a question, i.e. the title can't end with '?'.` |
| Post | Content contains ` sheep.` / ` lamb.` / ` cow.` / ` calf.` | (model) | `Sorry. Not allowed to end a sentance with 'sheep'.` (resp. `'lamb'`, `'cow'`, `'calf'`; legacy spelling "sentance" kept) |
| Post DTO | No blogger selected | Bloggers | `The blogger was not selected. You must do that before the post can be saved.` |
| Post DTO | Blogger id unknown | Bloggers | `Could not find the blogger you selected. Did another user delete it?` |
| Post DTO | No tag selected | UserChosenTags | `You must select at least one tag for the post.` |
| Post DTO | Tag id unknown | UserChosenTags | `Could not find one of the tags. Did another user delete it?` |
| Tag | Name / Slug required | Name / Slug | `The Name field is required.` / `The Slug field is required.` |
| Tag | Name max 128 / Slug max 64 | Name / Slug | `The field Name must be a string or array type with a maximum length of '128'.` (Slug: `'64'`) |
| Tag | Slug regex `\w*` (whole value) | Slug | `The slug must not contain spaces or non-alphanumeric characters.` |
| Tag | Slug unique (`SampleWebAppDb.ValidateEntity`) | Slug | `The Slug on tag '{Name}' must be unique and is already being used.` |
| Blog | Name required / min 2 / max 64 | Name | `The Name field is required.` / `The field Name must be a string or array type with a minimum length of '2'.` / `...maximum length of '64'.` |
| Blog | Email required / format / max 256 | EmailAddress | `The EmailAddress field is required.` / `The EmailAddress field is not a valid e-mail address.` / `...maximum length of '256'.` |

Legacy pages also run jQuery unobtrusive client validation before posting (e.g. empty Title is caught
client-side with the same text). The Java pages must at minimum produce the same messages server-side;
emitting matching `data-val-*` attributes so client validation also fires is a nice-to-have for B.

## 7. Seed data contract

Seed files are copied verbatim to `java/src/main/resources/seed/`. `DataLayerInitialiser.resetBlogs(sel)`
must, in one transaction: delete all posts (and join rows), tags and blogs; parse the XML; insert so that
display order matches the legacy run.

Insertion order (legacy EF graph order, observed in the baseline): for each `<Blog>` in document order
insert the blog, then each of its `<Post>`s in order; a tag row is created the **first time its slug is
referenced** by a post's `<TagSlugs>` (comma-separated, listed order). Tags never referenced are **not**
persisted. Blog `<Email>` maps to `EmailAddress`. `LastUpdated` = time of reset (UTC).

| Selection | File | Blogs (posts) | Persisted tags, TagId order |
|---|---|---|---|
| `SMALL` | `seed/BlogsContentSimple.xml` | Jon Smith `jon.smith@nospam.com` (2), Fred Bloggs (1) — 3 posts | Good post `good`, Ugly post `ugly`, Bad post `bad` |
| `MEDIUM` (Reset) | `seed/BlogsContextMedium.xml` | Ada Lovelace `ada@nospam.com` (5), Sherlock Holmes `sherlock@nospam.com` (5), Homer Simpson `doh@nospam.com` (3), Yoni the cat `yoni@nospam.com` (4) — 17 posts | About Me `about`, Programming `programming`, My Opinion `opinion`, Education `edu`, Vices and Foibles `vices`, Personal `personal`, Entertainment `entertain`, Business `biz` — **8 tags** (`mystery`, `murder` unused ⇒ not persisted) |

Posts list order is PostId ascending (= XML order). Post `tagNames` are ordered by TagId, e.g. a post with
`<TagSlugs>entertain,personal</TagSlugs>` shows `Personal, Entertainment`. Check against baseline
`06-posts-reset.png` and `33-tags-index.png` (post counts per tag).

Startup: the legacy app starts with an **empty** database; data appears after Posts → Reset. Java default
`app.seed-on-startup=false` reproduces that; `true` seeds MEDIUM when the DB is empty. Side-by-side
verification always starts by hitting `/Posts/Reset` on both apps so both hold identical seed data.

## 8. Tests contract (child C)

Port each NUnit fixture (54 legacy tests, all passing on the baseline VM) to a JUnit 5 class with the same
name and test-method intent:

| Legacy fixture | Tests | Java target |
|---|---|---|
| `Group01DataLayer/Test10SetupBlogs` | 5 | `@DataJpaTest` + `DataLayerInitialiser`: SMALL/MEDIUM seed counts and contents |
| `Group01DataLayer/Test13Validation` | 7 | Bean Validation + business rules → messages in §6 |
| `Group01DataLayer/Test14ReadWriteBlogs` | 13 | `@DataJpaTest` CRUD, cascade delete, many-to-many, slug uniqueness, LastUpdated |
| `Group03ServiceLayer/Test10DiSimple` | 11 | `@SpringBootTest`: services resolvable and list/detail/create/update/delete behave |
| `Group03ServiceLayer/Test11AutoFacModules` | 5 | `@SpringBootTest`: Spring wiring that replaces the Autofac modules |
| `Group06Mvc/Test02Validation` | 13 | `@WebMvcTest`/`MockMvc`: controller validation round-trips and messages |

Plus web-slice tests for every route in §5 (status, view name, flash message). H2 in-memory; Testcontainers
optional (Docker is not available on the coordinator VM). `mvn verify` writes `target/site/jacoco/`;
put a coverage summary table (line/branch per package) in the PR description.

## 9. Ownership and merge order

| Child | Branch (from `devin/java21-rationalisation`) | Owns |
|---|---|---|
| A — data + service | `devin/java21-a-data-service` | `datalayer/**`, `bizlayer/**`, `servicelayer/**` (implementations, validation, seed loader) |
| B — web | `devin/java21-b-web` | `web/**`, `templates/**`, `static/**`, web keys in `application.properties` |
| C — tests | `devin/java21-c-tests` | `src/test/**`, JaCoCo config in `pom.xml` |

B and C code against the contract interfaces. Before A merges they may use **test-scope** fakes/mocks but
must not add main-scope implementations of A's interfaces. Merge order A → B → C; each child rebases on the
integration branch after the previous merge and keeps `mvn verify` green on JDK 21.

## 10. Legacy baseline (Windows VM)

Captured on this Windows VM with IIS Express 10 + SQL Server LocalDB (`MSSQLLocalDB`), building the .NET
Framework 4.5.1 solution with VS 2022 MSBuild (`Microsoft.NETFramework.ReferenceAssemblies.net451` 1.0.3
for the targeting pack). Legacy NUnit suite: **54 / 54 passed**. 60-step browser walk, screenshots
`java/docs/baseline/NN-*.png`, machine-readable `java/docs/baseline/inventory.json`:

| # | Step | Path | Title | h2 | Rows | Message | Screenshot |
|---|---|---|---|---|---|---|---|
| 01 | home | `/` | Index - SampleMvcWebApp | Welcome to the Sample MVC Web Application - basic version |  |  | [png](java/docs/baseline/01-home.png) |
| 02 | about | `/Home/About` | About - SampleMvcWebApp | About this site |  |  | [png](java/docs/baseline/02-about.png) |
| 03 | contact | `/Home/Contact` | Contact - SampleMvcWebApp | Contact |  |  | [png](java/docs/baseline/03-contact.png) |
| 04 | home-codeview | `/Home/CodeView` | CodeView - SampleMvcWebApp | Introduction to GenericServices |  |  | [png](java/docs/baseline/04-home-codeview.png) |
| 05 | internals | `/Home/Internals` | - SampleMvcWebApp |  |  |  | [png](java/docs/baseline/05-internals.png) |
| 06 | posts-reset | `/Posts` | Posts - SampleMvcWebApp | Posts | 17 | Successfully reset the blogs data | [png](java/docs/baseline/06-posts-reset.png) |
| 07 | posts-details | `/Posts/Details/19` | Details - SampleMvcWebApp | Details |  |  | [png](java/docs/baseline/07-posts-details.png) |
| 08 | posts-numposts | `/Posts/NumPosts` | - SampleMvcWebApp |  |  |  | [png](java/docs/baseline/08-posts-numposts.png) |
| 09 | posts-delay | `/Posts/Delay` | Posts Delay - SampleMvcWebApp |  |  |  | [png](java/docs/baseline/09-posts-delay.png) |
| 10 | posts-codeview | `/Posts/CodeView` | CodeView - SampleMvcWebApp | Posts: An explanation of Post code |  |  | [png](java/docs/baseline/10-posts-codeview.png) |
| 11 | posts-create-get | `/Posts/Create` | Create - SampleMvcWebApp | Create | 10 |  | [png](java/docs/baseline/11-posts-create-get.png) |
| 12 | posts-create-empty-title | `/Posts/Create` | Create - SampleMvcWebApp | Create | 10 | The Title field is required. | [png](java/docs/baseline/12-posts-create-empty-title.png) |
| 13 | posts-create-bang-title | `/Posts/Create` | Create - SampleMvcWebApp | Create | 10 | Sorry, but you can't get too excited and include a ! in the title. | [png](java/docs/baseline/13-posts-create-bang-title.png) |
| 14 | posts-create-no-tags | `/Posts/Create` | Create - SampleMvcWebApp | Create | 10 | You must select at least one tag for the post. | [png](java/docs/baseline/14-posts-create-no-tags.png) |
| 15 | posts-create-ok | `/Posts` | Posts - SampleMvcWebApp | Posts | 18 | Successfully created Post. | [png](java/docs/baseline/15-posts-create-ok.png) |
| 16 | posts-edit-get | `/Posts/Edit/36` | Edit - SampleMvcWebApp | Edit | 10 |  | [png](java/docs/baseline/16-posts-edit-get.png) |
| 17 | posts-edit-ok | `/Posts` | Posts - SampleMvcWebApp | Posts | 18 | Successfully updated Post. | [png](java/docs/baseline/17-posts-edit-ok.png) |
| 18 | posts-delete | `/Posts` | Posts - SampleMvcWebApp | Posts | 17 | Successfully deleted Post. | [png](java/docs/baseline/18-posts-delete.png) |
| 19 | posts-filtered | `/Posts/Index/6` | Posts - SampleMvcWebApp | Posts | 5 | Filtered list | [png](java/docs/baseline/19-posts-filtered.png) |
| 20 | postsasync-reset | `/PostsAsync` | PostsAsync - SampleMvcWebApp | Posts (async) | 17 | Successfully reset the blogs data | [png](java/docs/baseline/20-postsasync-reset.png) |
| 21 | postsasync-details | `/PostsAsync/Details/37` | Details - SampleMvcWebApp | Details (Async) |  |  | [png](java/docs/baseline/21-postsasync-details.png) |
| 22 | postsasync-numposts | `/PostsAsync/NumPosts` | - SampleMvcWebApp |  |  |  | [png](java/docs/baseline/22-postsasync-numposts.png) |
| 23 | postsasync-delay | `/PostsAsync/Delay` | PostsAsync Delay - SampleMvcWebApp |  |  |  | [png](java/docs/baseline/23-postsasync-delay.png) |
| 24 | postsasync-codeview | `/PostsAsync/CodeView` | CodeView - SampleMvcWebApp | PostsAsync: A explanation of the PostAsync code |  |  | [png](java/docs/baseline/24-postsasync-codeview.png) |
| 25 | postsasync-create-get | `/PostsAsync/Create` | Create - SampleMvcWebApp | Create (Async) | 10 |  | [png](java/docs/baseline/25-postsasync-create-get.png) |
| 26 | postsasync-create-empty-title | `/PostsAsync/Create` | Create - SampleMvcWebApp | Create (Async) | 10 | The Title field is required. | [png](java/docs/baseline/26-postsasync-create-empty-title.png) |
| 27 | postsasync-create-bang-title | `/PostsAsync/Create` | Create - SampleMvcWebApp | Create (Async) | 10 | Sorry, but you can't get too excited and include a ! in the title. | [png](java/docs/baseline/27-postsasync-create-bang-title.png) |
| 28 | postsasync-create-no-tags | `/PostsAsync/Create` | Create - SampleMvcWebApp | Create (Async) | 10 | You must select at least one tag for the post. | [png](java/docs/baseline/28-postsasync-create-no-tags.png) |
| 29 | postsasync-create-ok | `/PostsAsync` | PostsAsync - SampleMvcWebApp | Posts (async) | 18 | Successfully created Post. | [png](java/docs/baseline/29-postsasync-create-ok.png) |
| 30 | postsasync-edit-get | `/PostsAsync/Edit/54` | Edit - SampleMvcWebApp | Edit (Async) | 10 |  | [png](java/docs/baseline/30-postsasync-edit-get.png) |
| 31 | postsasync-edit-ok | `/PostsAsync` | PostsAsync - SampleMvcWebApp | Posts (async) | 18 | Successfully updated Post. | [png](java/docs/baseline/31-postsasync-edit-ok.png) |
| 32 | postsasync-delete | `/PostsAsync` | PostsAsync - SampleMvcWebApp | Posts (async) | 17 | Successfully deleted Post. | [png](java/docs/baseline/32-postsasync-delete.png) |
| 33 | tags-index | `/Tags` | Tags - SampleMvcWebApp | Tags | 8 |  | [png](java/docs/baseline/33-tags-index.png) |
| 34 | tags-details | `/Tags/Details/18` | Details - SampleMvcWebApp | Details |  |  | [png](java/docs/baseline/34-tags-details.png) |
| 35 | tags-codeview | `/Tags/CodeView` | CodeView - SampleMvcWebApp | Tags: A explanation of Tags code |  |  | [png](java/docs/baseline/35-tags-codeview.png) |
| 36 | tags-create-get | `/Tags/Create` | Create - SampleMvcWebApp | Create | 8 |  | [png](java/docs/baseline/36-tags-create-get.png) |
| 37 | tags-create-dup-slug | `/Tags/Create` | Create - SampleMvcWebApp | Create | 8 | The Slug on tag 'Duplicate' must be unique and is already being used. | [png](java/docs/baseline/37-tags-create-dup-slug.png) |
| 38 | tags-create-bad-slug | `/Tags/Create` | Create - SampleMvcWebApp | Create | 8 | The slug must not contain spaces or non-alphanumeric characters. | [png](java/docs/baseline/38-tags-create-bad-slug.png) |
| 39 | tags-create-ok | `/Tags` | Tags - SampleMvcWebApp | Tags | 9 | Successfully created Tag. | [png](java/docs/baseline/39-tags-create-ok.png) |
| 40 | tags-edit-get | `/Tags/Edit/25` | Edit - SampleMvcWebApp | Edit | 8 |  | [png](java/docs/baseline/40-tags-edit-get.png) |
| 41 | tags-edit-ok | `/Tags` | Tags - SampleMvcWebApp | Tags | 9 | Successfully updated Tag. | [png](java/docs/baseline/41-tags-edit-ok.png) |
| 42 | tags-delete | `/Tags` | Tags - SampleMvcWebApp | Tags | 8 | Successfully deleted Tag. | [png](java/docs/baseline/42-tags-delete.png) |
| 43 | tagsasync-index | `/TagsAsync` | TagsAsync - SampleMvcWebApp | Tags (Async) | 8 |  | [png](java/docs/baseline/43-tagsasync-index.png) |
| 44 | tagsasync-details | `/TagsAsync/Details/18` | Details - SampleMvcWebApp | Details (Async) |  |  | [png](java/docs/baseline/44-tagsasync-details.png) |
| 45 | tagsasync-codeview | `/TagsAsync/CodeView` | CodeView - SampleMvcWebApp | Tags: A explanation of Tags code |  |  | [png](java/docs/baseline/45-tagsasync-codeview.png) |
| 46 | tagsasync-create-get | `/TagsAsync/Create` | Create - SampleMvcWebApp | Create (Async) | 8 |  | [png](java/docs/baseline/46-tagsasync-create-get.png) |
| 47 | tagsasync-create-dup-slug | `/TagsAsync/Create` | Create - SampleMvcWebApp | Create (Async) | 8 | The Slug on tag 'Duplicate' must be unique and is already being used. | [png](java/docs/baseline/47-tagsasync-create-dup-slug.png) |
| 48 | tagsasync-create-bad-slug | `/TagsAsync/Create` | Create - SampleMvcWebApp | Create (Async) | 8 | The slug must not contain spaces or non-alphanumeric characters. | [png](java/docs/baseline/48-tagsasync-create-bad-slug.png) |
| 49 | tagsasync-create-ok | `/TagsAsync` | TagsAsync - SampleMvcWebApp | Tags (Async) | 9 | Successfully created Tag. | [png](java/docs/baseline/49-tagsasync-create-ok.png) |
| 50 | tagsasync-edit-get | `/TagsAsync/Edit/26` | Edit - SampleMvcWebApp | Edit (Async) | 8 |  | [png](java/docs/baseline/50-tagsasync-edit-get.png) |
| 51 | tagsasync-edit-ok | `/TagsAsync` | TagsAsync - SampleMvcWebApp | Tags (Async) | 9 | Successfully updated Tag. | [png](java/docs/baseline/51-tagsasync-edit-ok.png) |
| 52 | tagsasync-delete | `/TagsAsync` | TagsAsync - SampleMvcWebApp | Tags (Async) | 8 | Successfully deleted Tag. | [png](java/docs/baseline/52-tagsasync-delete.png) |
| 53 | blogs-index | `/Blogs` | - SampleMvcWebApp | Blogs | 4 |  | [png](java/docs/baseline/53-blogs-index.png) |
| 54 | blogs-create-get | `/Blogs/Create` | - SampleMvcWebApp |  |  |  | [png](java/docs/baseline/54-blogs-create-get.png) |
| 55 | blogs-create-invalid | `/Blogs/Create` | - SampleMvcWebApp |  |  |  | [png](java/docs/baseline/55-blogs-create-invalid.png) |
| 56 | blogs-create-ok | `/Blogs` | - SampleMvcWebApp | Blogs | 5 | Successfully created Blog. | [png](java/docs/baseline/56-blogs-create-ok.png) |
| 57 | blogs-edit-get | `/Blogs/Edit/13` | - SampleMvcWebApp |  |  |  | [png](java/docs/baseline/57-blogs-edit-get.png) |
| 58 | blogs-edit-ok | `/Blogs` | - SampleMvcWebApp | Blogs | 5 | Successfully updated Blog. | [png](java/docs/baseline/58-blogs-edit-ok.png) |
| 59 | blogs-delete | `/Blogs` | - SampleMvcWebApp | Blogs | 4 | Successfully deleted Blog. | [png](java/docs/baseline/59-blogs-delete.png) |
| 60 | final-reset | `/Posts` | Posts - SampleMvcWebApp | Posts | 17 | Successfully reset the blogs data | [png](java/docs/baseline/60-final-reset.png) |
