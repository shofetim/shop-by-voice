#!/usr/bin/fish

echo "Remember to set the auth token"

for i in cat(products.txt)
         curl -XPOST 'https://api.wit.ai/entities/product/values' \
              -H 'Authorization: Bearer '$TOKEN \
              -H 'Content-Type: application/json' \
              -d '{"value":"'$i'"}'
end
