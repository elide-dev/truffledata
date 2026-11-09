#ifndef TRUFFLEDATA_JSON_TESTING_ONDEMAND_POOL_H
#define TRUFFLEDATA_JSON_TESTING_ONDEMAND_POOL_H
#include "json/native/parser_pool.h"
// Only historical test producers lease an On-Demand parser. Production leases stage1_index.
namespace truffledata::json {
using parser_lease = basic_parser_lease<simdjson::ondemand::parser>;
using parser_pool = basic_parser_pool<simdjson::ondemand::parser>;
}
#endif
