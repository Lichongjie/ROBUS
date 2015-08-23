select sum(l_extendedprice) / 7.0 as avg_yearly from (select l_quantity, l_extendedprice, t_avg_quantity
from (select l_partkey as t_partkey, 0.2 * avg(l_quantity) as t_avg_quantity from lineitem_cache group by
l_partkey) t join (select l_quantity, l_partkey, l_extendedprice from part_cache p join lineitem_cache l
on p.p_partkey = l.l_partkey and p.p_brand = 'Brand#23' and p.p_container = 'MED BOX') l1 on l1.l_partkey
= t.t_partkey) a where l_quantity < t_avg_quantity
