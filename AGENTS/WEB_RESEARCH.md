# Web Research and Internet Token Discipline

Read this file only when the task actually requires internet access.

Internet research is expensive in both tool calls and context. Use it selectively.

## When to browse

Browse only when at least one of these is true:
- the task depends on current information;
- a library/API/platform behavior is not established by the local source or installed documentation;
- an exact external specification, compatibility fact, release note, or known issue is required;
- a local failure cannot be explained from available code/logs and external documentation is likely to resolve it;
- the user explicitly requested web research.

Do not browse merely for reassurance, general background, or facts already established by repository code, local docs, prior verified results, or current context.

## Search strategy

Before searching:
1. State internally the exact unknown that blocks progress.
2. Search for that unknown, not the whole topic.
3. Include the product/library/API name, relevant version when known, and exact error/signature when useful.

Default research path:
1. One precise search query.
2. Prefer official documentation, upstream source/repository, specification, or primary issue tracker.
3. Open only the most relevant result(s).
4. Extract only the facts needed for the implementation decision.
5. Stop once the unknown is resolved.

Prefer 1–3 high-signal sources over broad result collection.

When multiple independent facts are already known to require lookup, batch the precise searches in one web research phase when the tool supports it. Do not alternate `model → one search → model → one search` for predictable lookups.

Do not:
- run several paraphrases of the same query unless the first query failed;
- search both broad and narrow forms when a narrow query is sufficient;
- browse blogs/forums when primary documentation answers the question;
- open many search results “just in case”;
- perform image/video search unless the task is genuinely visual;
- recursively follow links unrelated to the blocking question;
- reread the same web page during the same task unless new sections are required;
- search for generic UX/design inspiration when `TASK.md` already defines the intended product direction;
- use web search to reconfirm facts already established by primary sources earlier in the same task.

## Error research

For an external error:
- search the shortest distinctive exact error fragment plus component/library;
- include version/platform only when it materially narrows results;
- inspect the official/upstream issue or docs first;
- stop after a credible explanation and actionable fix are found.

Do not paste whole logs into search queries. Use only the distinctive portion.

## Documentation research

When consulting API/docs:
- retrieve the exact page/section for the API involved;
- extract signatures, constraints, defaults, lifecycle rules, and compatibility notes only;
- do not ingest tutorials or entire guides if one reference section is enough.

If a direct official URL is already known, open it instead of searching for it again.

## Reuse and caching

Within a task, remember and reuse:
- URLs already opened;
- relevant version numbers;
- verified API behavior;
- issue numbers;
- exact constraints and decisions derived from sources.

Do not search again unless code/version/scope changed or the earlier evidence was insufficient.

## Reporting web research

Do not narrate each query/open action.

In the final response, mention only web-derived facts that affected the implementation or decision, with concise source references when appropriate.

If web research produces no actionable new information, do not spend additional searches trying to manufacture certainty. Return to local evidence or state the remaining uncertainty.
