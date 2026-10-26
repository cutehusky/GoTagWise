package smoke

type WrongWrappers struct {
	Flag      GenericValue[bool]
	Email     GenericValue[int]        `validate:"email"`
	Minimum   GenericValue[string]     `minimum:"1"`
	JSON      GenericValue[[]int]      `json:",string"`
	Dive      GenericValue[string]     `validate:"dive"`
	Pointer   *GenericValue[string]    `query:"q"`
	Condition string                   `validate:"required_if=Flag yes"`
	Pair      GenericPair[string, int] `validate:"email"`
}

type CorrectWrappers struct {
	Count        int
	Email        GenericValue[string]               `validate:"required,email"`
	Lower        LowerValue[string]                 `validate:"email"`
	Nested       GenericValue[GenericValue[string]] `validate:"email"`
	Values       GenericValue[[]string]             `validate:"dive,email"`
	Mapping      GenericValue[map[int]string]       `validate:"dive,keys,numeric,endkeys,email"`
	Alias        WrappedAlias                       `validate:"email"`
	PointerValue GenericValue[*string]              `query:"q"`
	Compare      GenericValue[int]                  `validate:"eqfield=Count"`
	Plain        PlainValue                         `minProperties:"1"`
	Fixed        FixedValue[string]                 `minProperties:"1"`
	Named        NamedValue                         `minProperties:"1"`
	Pair         GenericPair[int, string]           `validate:"email"`
}
