with recursive tree as (
    select id, name, parent_id, 1 as level from account where parent_id is null
    union all
    select account.id, tree.name || ':' || account.name, account.parent_id, (tree.level + 1) as level from account join tree on account.parent_id = tree.id
) select * from tree;