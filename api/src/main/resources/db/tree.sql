with recursive tree as (
    select id, name::text, accounts.parent_account_id, 1 as level from accounts where parent_account_id is null
    union all
    select accounts.id, (tree.name || ':' || accounts.name)::text, accounts.parent_account_id, (tree.level + 1) as level from accounts join tree on accounts.parent_account_id = tree.id
) select * from tree;
